package norswap.uranium;

import norswap.utils.NArrays;
import norswap.utils.multimap.MultiHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static norswap.utils.Util.cast;

/**
 * A reactor is a collection of {@link Rule rules} that export attribute values, given some
 * attribute values as input. It also maintains a store of {@link Attribute attribute} values.
 *
 * <p>Rules can be registered using the builder returned by the {@link #rule} methods.
 *
 * <p>Simple attributes values can be set using {@link #set}, but most attributes will be set
 * automatically when the rules are run. Similarly, simple errors can be set directly with
 * {@link #error}.
 *
 * <p>Call the {@link #run()} method to start the reactor. This will run every applicable rule,
 * until no more rule can be run.
 *
 * <p>During the execution, rules may generate errors. These errors may be retrieved using {@link
 * #errors()} (for "root" errors) or {@link #allErrors()} to get all errors, including derived
 * errors (where we couldn't compute the value of an attribute because there was another error
 * preventing the computation of a dependency).
 */
public class Reactor
{
    // =============================================================================================
    // region [Fields]
    // =============================================================================================

    private static final Attribute NO_DEPS = new Attribute(null, "NO_DEPS");
    private final HashMap<Attribute, Object> attributes = new HashMap<>();
    private final MultiHashMap<Attribute, Rule> dependencies = new MultiHashMap<>();
    private final ArrayDeque<Rule> queue = new ArrayDeque<>();
    private final HashSet<SemanticError> errors = new HashSet<>();
    private boolean running = false;

    // endregion
    // =============================================================================================
    // region [Inspect]
    // =============================================================================================

    /**
     * Get the value of the given attribute, or null if it hasn't been computed.
     */
    public final Object get (Attribute attribute) {
        return attributes.get(attribute);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Get the value of the given attribute, or null if it hasn't been computed.
     */
    public final Object get (Object node, String name) {
        return attributes.get(new Attribute(node, name));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Return a stream of all the (attribute, value) pairs for attributes on the given node.
     */
    public final Stream<Map.Entry<Attribute, Object>> getAll (Object node) {
        return attributes.entrySet().stream()
            .filter(p -> p.getKey().node == node);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the set of valued attribute.
     */
    public final Set<Attribute> getAttributes() {
        return attributes.keySet();
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the list of root errors encountered while running the reactor. These are errors that
     * are not caused by another error, excepted for derived errors that are not attached to a
     * particular attribute.
     *
     * <p>Use {@link #allErrors()} to get all errors, including derived errors.
     */
    public final Set<SemanticError> errors() {
        return Collections.unmodifiableSet(errors);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the list of all errors obtained while running the reactor. This includes root errors
     * (as per {@link #errors()} but also derived errors that were caused by another error. This
     * most notably includes propagated errors caused by another error precluding the computation of
     * a dependency attribute.
     */
    public final List<SemanticError> allErrors()
    {
        ArrayList<SemanticError> list = new ArrayList<>(errors);
        for (Object value: attributes.values()) {
            if (value instanceof SemanticError) {
                SemanticError error = cast(value);
                // Root errors are already in the list.
                // We do include them separately, because we don't want them to be in the list
                // even if the attribute that wasn't provided was provided by another rule.
                if (error.cause != null)
                    list.add(cast(value));
            }
        }
        return list;
    }

    // endregion
    // =============================================================================================
    // region [Eager Attribute Suppliers]
    // =============================================================================================

    /**
     * Set the value of the given attribute.
     *
     * <p>This should be used to set trivial attributes. It is also possible to define rules
     * with no dependencies for more complex operations.
     *
     * <p>This should be called in rules themselves: the reactor will automatically set the value
     * of rule's exported attributes once the rule has run.
     */
    public void set (Attribute attribute, Object value) {
        attributes.put(attribute, value);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Set the value of the given attribute.
     *
     * <p>This should be used to set trivial attributes. It is also possible to define rules
     * with no dependencies for more complex operations.
     *
     * <p>This should be called in rules themselves: the reactor will automatically set the value
     * of rule's exported attributes once the rule has run.
     */
    public void set (Object node, String name, Object value) {
        set(new Attribute(node, name), value);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to report that a semantic error occurred, affecting the {@code affected} attributes
     * (this list can be empty).
     *
     * <p>This should be used for simple errors that are independent of attribute values and
     * can be detected as rule instantiation time. It's often used for "syntactic errors" that are
     * more cleanly handled by inspecting the tree during semantic analysis than by modifying
     * the parser.
     */
    public void error (SemanticError error, Attribute... affected)
    {
        if (affected.length == 0) {
            reportError(error, null);
            return;
        }

        for (Attribute attr: affected)
            reportError(error, attr);
    }

    // endregion
    // =============================================================================================
    // region [Run Loop]
    // =============================================================================================

    /**
     * Run all the rules that can be run (directly, or transitively, as rules make new attributes
     * available).
     */
    public final void run()
    {
        running = true;

        attributes.forEach((k, v) ->
            dependencies.get(k).forEach(r -> r.supply(k, v)));

        dependencies.get(NO_DEPS).forEach(this::enqueue);

        while (!queue.isEmpty())
        {
            Rule rule = queue.removeFirst();
            rule.run();

            for (int i = 0; i < rule.exports.length; ++i)
            {
                Attribute attribute = rule.exports[i];
                Object value = rule.exportValues[i];

                if (value == null)
                    throw new IllegalStateException(String.format(
                        "rule did not provide exported attribute %s:\n%s", attribute, rule));

                Object old = attributes.putIfAbsent(attribute, value);

                if (old != null)
                    attributeRedefinitionAttempt(attribute, old, value);
                else if (value instanceof SemanticError)
                    reportError((SemanticError) value, attribute);
                else
                    supplyToDependents(attribute, value);
            }
        }

        running = false;
    }

    // ---------------------------------------------------------------------------------------------

    final void enqueue(Rule rule) {
        queue.addLast(rule);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Called when we attempt to redefine an attribute value. This can happen if the same attribute
     * is exported by multiple rules. The default implementation throws an {@link Error}.
     *
     * <p>If we override this method, you can call {@link #set} and {@link #supplyToDependents} to
     * change this behaviour. Note that prior to calling this method, the original value is kept and
     * rules depending on this attribute have not been notified of the new value.
     */
    protected void attributeRedefinitionAttempt
    (Attribute attribute, Object oldValue, Object newValue) {
        throw new Error("attempting to redefine: "+  attribute);
    }

    // ---------------------------------------------------------------------------------------------

    final void reportError (SemanticError error, Attribute affected)
    {
        if (error.cause == null) errors.add(error);

        if (affected == null) {
            if (error.cause != null) errors.add(error); // otherwise these errors would be lost
            return;
        }

        Object value = attributes.get(affected);
        if (value != null)
            attributeRedefinitionAttempt(affected, value, error);

        attributes.put(affected, error); // the error becomes the attribute value
        propagateError(error, affected);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Signifies to each rule depending on the attribute that it has received a new value (which may
     * cause the rule to become runnable by the reactor).
     *
     * <p>Call from {@link #attributeRedefinitionAttempt} to cause depending rules to see the new
     * value and re-run those that have already ran.
     */
    protected final void supplyToDependents (Attribute attribute, Object value) {
        dependencies.get(attribute).forEach(r -> r.supply(attribute, value));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Propagates the given {@code error} that precludes the computation of the {@code affected}
     * attribute, by signaling an error on every exported attributes of all the rules that depend
     * on the affected attribute.
     *
     * <p>Can be called from {@link #attributeRedefinitionAttempt}. Note that if the attributes
     * affected by the propagated error already have a value/error, then {@link
     * #attributeRedefinitionAttempt} could be called recursively.
     */
    protected final void propagateError (SemanticError error, Attribute affected)
    {
        for (Rule dependent: dependencies.get(affected)) // propagate the error
            for (Attribute exported: dependent.exports)
                reportError(new SemanticError("missing dependency " + affected, error, null),
                    exported);
    }

    // endregion
    // =============================================================================================
    // region [Building Rules]
    // =============================================================================================

    /**
     * Return a builder to define a rule that exports the given attributes. Can be empty!
     */
    public final Rule.Builder rule (Attribute... exports) {
        return new Rule.Builder(this, exports);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Return a builder to define a rule that exports the given attribute.
     */
    public final Rule.Builder rule (Object node, String name) {
        return new Rule.Builder(this, NArrays.array(new Attribute(node, name)));
    }

    // ---------------------------------------------------------------------------------------------

    // called by Rule.Builder#by
    final void register (Rule rule)
    {
        if (rule.dependencies.length == 0) {
            dependencies.add(NO_DEPS, rule);
            if (running) enqueue(rule);
            return;
        }

        for (Attribute dependency: rule.dependencies) {
            dependencies.add(dependency, rule);
            if (running) {
                Object value = get(dependency);
                if (value != null) rule.supply(dependency, value);
            }
        }
    }

    // endregion
    // =============================================================================================
}
