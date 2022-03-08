package norswap.uranium;

import norswap.utils.NArrays;
import norswap.utils.Strings;
import norswap.utils.multimap.MultiHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
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
    private final HashSet<SemanticError> attributelessDerivedErrors = new HashSet<>();
    private boolean running = false;

    // endregion
    // =============================================================================================
    // region [Inspect]
    // =============================================================================================

    /**
     * Get the value of the given attribute, or null if it hasn't been computed.
     * The value will be automatically cast to the target type.
     */
    public final <T> T get (Attribute attribute) {
        return cast(attributes.get(attribute));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Get the value of the given attribute, or null if it hasn't been computed.
     * The value will be automatically cast to the target type.
     */
    public final <T> T get (Object node, String name) {
        return cast(attributes.get(new Attribute(node, name)));
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
        list.addAll(attributelessDerivedErrors);
        return list;
    }

    // endregion
    // =============================================================================================
    // region [Eager Attribute Suppliers]
    // =============================================================================================

    /**
     * Set the value (non-null) of the given attribute that can be known statically, <b>before
     * running the reactor</b>. This is not meant for use in rules (use {@link Rule#set(int,
     * Object)} and variants).
     *
     * @throws IllegalStateException if called while the reactor is running
     */
    public void set (Attribute attribute, Object value) {
        if (value == null) throw new IllegalArgumentException("value can't be null");
        if (running) throw new IllegalStateException(
            "Calling Reactor#set while the reactor is running - " +
            "see Javadoc of that method for details.");
        attributes.put(attribute, value);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Set the value (non-null) of the given attribute that can be known statically, <b>before
     * running the reactor</b>. This is not meant for use in rules (use {@link Rule#set(int,
     * Object)} and variants).
     *
     * @throws IllegalStateException if called while the reactor is running
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
        loopOnQueue();
        handleMissingAttributes();
        running = false;
    }

    // ---------------------------------------------------------------------------------------------

    final void enqueue(Rule rule) {
        queue.addLast(rule);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Loops in the queue until it is empty, running each queued rule and propagating
     * its exported values (which may in turn cause more rules to become enqueued).
     */
    private void loopOnQueue()
    {
        while (!queue.isEmpty()) {
            Rule rule = queue.removeFirst();

            try {
                rule.run();
            } catch (RuntimeException e) {
                throw new RuntimeException("exception while running: " + rule, e);
            }

            for (int i = 0; i < rule.exports.length; ++i) {
                Attribute attribute = rule.exports[i];
                Object value = rule.exportValues[i];
                if (value == null) throw new IllegalStateException(
                    format("rule did not provide exported attribute %s:\n%s", attribute, rule));
                setValue(attribute, value);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    @SuppressWarnings("StatementWithEmptyBody")
    private void setValue (Attribute attribute, Object value)
    {
        Object old = attributes.putIfAbsent(attribute, value);

        if (old instanceof SemanticError) {
            // For now: skip and keep the first reported error. In the future, might want to let the
            // user pick - but that required changing error propagation to change the whole chain.
        } else if (old != null) {
            attributeRedefinitionAttempt(attribute, old, value);
        } else if (value instanceof SemanticError) {
            SemanticError error = (SemanticError) value;
            if (error.cause == null) errors.add(error);
            propagateError(error, attribute);
        } else {
            supplyToDependents(attribute, value);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Called when we attempt to redefine an attribute value. This can happen if the same attribute
     * is exported by multiple rules. The default implementation throws an {@link Error}.
     *
     * <p>If you override this method, you can call {@link #redefine} and {@link
     * #supplyToDependents} to change this behaviour. Note that prior to calling this method, the
     * original value is kept and rules depending on this attribute have not been notified of the
     * new value.
     */
    protected void attributeRedefinitionAttempt (Attribute attribute, Object oldV, Object newV) {
        throw new Error("attempting to redefine: " +  attribute);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Change the value of an already-defined attribute. Meant to be called from {@link
     * #attributeRedefinitionAttempt}
     */
    protected void redefine (Attribute attribute, Object value) {
        if (value == null) throw new IllegalArgumentException("value can't be null");
        attributes.put(attribute, value);
    }

    // ---------------------------------------------------------------------------------------------

    final void reportError (SemanticError error, Attribute affected)
    {
        if (affected == null) {
            // otherwise these errors would be lost
            if (error.cause == null) errors.add(error);
            else attributelessDerivedErrors.add(error);
            return;
        }
        setValue(affected, error);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Propagates the given {@code error} that precludes the computation of the {@code affected}
     * attribute, by signaling an error on every exported attributes of all the rules that depend
     * on the affected attribute.
     *
     * <p>For now, we don't generate errors for rules with no exported dependencies: they have no
     * name so such an error wouldn't be informative, and the propagated error will still exist.
     *
     * <p>Can be called from {@link #attributeRedefinitionAttempt}. Note that if the attributes
     * affected by the propagated error already have a value/error, then {@link
     * #attributeRedefinitionAttempt} could be called recursively.
     */
    protected final void propagateError (SemanticError error, Attribute affected)
    {
        dependencies.get(affected).stream()
            .flatMap(rule -> Arrays.stream(rule.exports))
            .forEach(exported -> reportError(
                new SemanticError("missing dependency " + affected, error, null),
                exported));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Signifies to each rule depending on the attribute that it has received a new value (which may
     * cause the rule to become runnable by the reactor).
     *
     * <p>Call from {@link #attributeRedefinitionAttempt} to cause depending rules to see the new
     * value and re-run those that have already ran.
     *
     * <p>If you call this, you must have have called {@link #redefine} beforehand!
     */
    protected final void supplyToDependents (Attribute attribute, Object value) {
        dependencies.get(attribute).forEach(r -> r.supply(attribute, value));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Look for rules that haven't run and process them. We exclude rules that haven't run
     * because one of their attributes had an error value. The remaining cases should only occur
     * when the user failed to supply an attribute's value or error (i.e. it is a bug in the
     * user's semantic analysis logic).
     *
     * <p>We generate errors for these missing attributes, and propagate them in the usual way.
     */
    private void handleMissingAttributes()
    {
        List<Rule> untriggeredRules = dependencies.values().stream()
            .flatMap(Collection::stream)
            .filter(rule -> rule.unsatisfied > 0) // haven't run
            .filter(rule -> Arrays.stream(rule.dependencies)
                // exclude rules with error as dependencies
                .noneMatch(dep -> attributes.get(dep) instanceof SemanticError))
            .collect(Collectors.toList());

        Set<Attribute> untriggeredExports = untriggeredRules.stream()
            .flatMap(rule -> Arrays.stream(rule.exports))
            .collect(Collectors.toSet());

        untriggeredRules.stream()
            .flatMap(rule -> Arrays.stream(rule.dependencies))
            // get missing dependencies that cannot be obtained indirectly via an untriggered rule
            .filter(dep -> attributes.get(dep) == null && !untriggeredExports.contains(dep))
            .forEach(dep -> setValue(dep,
                new SemanticError("missing attribute " + dep, null, dep.node)));

        // Indirectly missing dependencies will still get an error through error propagation.

        // No need to reprocess the queue: error propagation is instantaneous, and makes no
        // new rules runnable.
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
    // region Reporting
    // =============================================================================================

    public String reportErrors (Function<Object, String> printLocation)
    {
        if (errors.isEmpty())
            return "";

        StringBuilder b = new StringBuilder();

        for (SemanticError error: errors)
        {
            b.append(error.description);
            Object location = error.location();
            if (location != null) {
                b.append("\nlocation: ");
                b.append(printLocation.apply(location));
            }
            b.append("\n\n");
        }

        Strings.pop(b, 1); // last newline
        return b.toString();
    }

    // endregion
    // =============================================================================================
}
