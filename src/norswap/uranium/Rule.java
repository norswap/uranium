package norswap.uranium;

import norswap.utils.NArrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static norswap.utils.NArrays.indexOf;
import static norswap.utils.Util.cast;

/**
 * A rule computes a set of exported attribute values, using a set of dependencies attribute values.
 *
 * <p>A {@code Rule} is created by calling one of the {@link Reactor#rule} method and specifiying
 * the exported attributes, dependencies attributes ({@link Builder#using} methods), as well as the
 * computation logic to go from one to the other ({@link Builder#by(Consumer)}. No value are
 * suppplied at this stage.
 *
 * <p>A {@code Rule} must not (and can not) be invoked manually by the language implementer, instead
 * they are registered to a {@link Reactor} which invokes them when approriate.
 *
 * <p>The language implementer can access the {@code Rule} object from the logic passed to {@link
 * Builder#by(Consumer)}. It is used to access the value of dependencies attributes ({@link #get}),
 * set the value of exports attributes {@link #set}, and report semantic errors ({@link #error} and
 * {@link #errorFor}.
 *
 * <h2>Rule Computation Contract</h2>
 *
 * <p>A rule must supply the value of all its exported attributes (by means of one of the {@link
 * #set} methods), or signal that a semantic error prevented their computation (by means of one of
 * the {@link #error} or {@link #errorFor} methods). Failure to do so will cause the {@link
 * Reactor} to throw an exception. Supplies values may not be {@code null}.
 *
 * <p>When an error is signaled for an attribute, that error will be set as the value of attribute.
 * The error will be propagated (signaled) to rules that depend on the attribute (resulting to all
 * <b>their</b> exported attribues to be signaled as error too). The rule computation for these
 * rules is of course not triggered.
 *
 * <p>It's also possible to signal an error that does not preclude the computation of any
 * attribute, by leaving the attribute list empty when calling {@link #errorFor}.
 *
 * <p>By default, multiple rules are not allowed to compete to provide the value of a single
 * attribute. However, this can be made possible by overriding the {@link
 * Reactor#attributeRedefinitionAttempt} (which by default throws an exception). This can notably
 * be used to enable incremental attribute computation, where each rule can be rerun as its
 * dependency values change. In the default configuration, each rule is guaranteed to be ran
 * at most once.
 *
 * <h2>Lazy/Chained Rules</h2>
 *
 * <p>It is possible to instantiate new rules from within a rule. We call this pattern "lazy rules"
 * or "chained rules". This is often necessary, because we need to dynamically lookup a node before
 * we can use it as as part of an attribute. For instance the node could be the result of a scope
 * lookup that allows use-before-declaration, or it could be the value of a computed attribute.
 *
 * <p>Lazy rules are not special, they just happen to be instantiated later than the other rules
 * (which are typically instantiated while doing a single AST walk before starting the {@link
 * Reactor}.
 *
 * <p>However, it is worth paying attention to reported errors when using lazy rules. In particular,
 * when an error early in a rule prevents the instantiation of a lazy rule, then you should signal
 * that the error prevents the computation of the lazy rule's exports, using one of the {@link
 * #errorFor} methods. You can also instantiate the rule regardless, and let the error be
 * propagated to it.
 */
public final class Rule
{
    // =============================================================================================
    // region [Initialization]
    // =============================================================================================

    public final Attribute[] exports;
    public final Attribute[] dependencies;
    final Object[] exportValues;
    private final Object[] dependencyValues;

    private final Reactor reactor;
    private final Consumer<Rule> computation;
    int unsatisfied;

    // ---------------------------------------------------------------------------------------------

    private Rule (Reactor reactor, Attribute[] exports, Attribute[] dependencies,
            Consumer<Rule> computation)
    {
        this.dependencies = dependencies;
        this.exports = exports;
        this.dependencyValues = new Object[dependencies.length];
        this.exportValues = new Object[exports.length];
        this.reactor = reactor;
        this.computation = computation;
        this.unsatisfied = this.dependencies.length;
    }

    // ---------------------------------------------------------------------------------------------

    @Override public String toString()
    {
        String dependenciesString = IntStream.range(0, dependencies.length).mapToObj(i -> {
            Object value = dependencyValues[i];
            return value == null
                ? "" + dependencies[i]
                : dependencies[i] + " = " + value;
        }).collect(Collectors.joining(", "));

        String exportsString = IntStream.range(0, exports.length).mapToObj(i -> {
            Object value = exportValues[i];
            return value == null
                ? "" + exports[i]
                : exports[i] + " = " + value;
        }).collect(Collectors.joining(", "));

        return String.format("Rule{\n  deps: [%s]\n  exports: [%s]\n}",
            dependenciesString, exportsString);
    }

    // endregion
    // =============================================================================================
    // region [Reactor Callbacks]
    // =============================================================================================

    void run() {
        assert unsatisfied == 0;
        computation.accept(this);
    }

    // ---------------------------------------------------------------------------------------------

    /** Called by the Reactor to supply a dependency value. */
    void supply (Attribute dependency, Object value) {
        // the same attribute might be present multiple times
        for (int i = 0; i < dependencies.length; i++) {
            if (dependencies[i].equals(dependency)) {
                Object old = dependencyValues[i];
                dependencyValues[i] = value;
                // Because of redefinitions, this could be run multiple times, only decrement if
                // this is not a redefinition, so as not to skip a missing dependency.
                if (old == null)
                    -- unsatisfied;
                if (unsatisfied == 0)
                    // In case of redefinition, the rule could fire multiple times.
                    // This is intended behaviour.
                    reactor.enqueue(this);
            }
        }
    }

    // endregion
    // =============================================================================================
    // region [Retrieving Dependency Values]
    // =============================================================================================

    /**
     * Retrieve the value of the given dependency. Implicit casting to the target type is performed.
     */
    public <T> T get (Attribute dependency)
    {
        int i = NArrays.indexOf(dependencies, dependency);
        Object value = dependencyValues[i];
        if (value == null)
            value = dependencyValues[i] = reactor.get(dependency);
        return cast(value);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Retrieve the value of the given dependency. Implicit casting to the target type is performed.
     */
    public <T> T get (Object node, String name) {
        return get(new Attribute(node, name));
    }
    // ---------------------------------------------------------------------------------------------

    /**
     * Retrieve the value of dependency at the give index. Implicit casting to the target type is
     * performed.
     */
    public <T> T get (int index) {
        return cast(dependencyValues[index]);
    }

    // endregion
    // =============================================================================================
    // region [Supplying Exported Values]
    // =============================================================================================

    /**
     * Sets the value of the given export, which cannot be null.
     */
    public void set (Attribute export, Object value)
    {
        if (value == null) throw new IllegalArgumentException("value can't be null");
        int i = NArrays.indexOf(exports, export);
        exportValues[i] = value;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Sets the value of the given export, which cannot be null.
     */
    public void set (Object node, String name, Object value) {
        set(new Attribute(node, name), value);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Set the value of the dependency at the given index, which cannot be null.
     */
    public void set (int index, Object value) {
        if (value == null) throw new IllegalArgumentException("value can't be null");
        exportValues[index] = value;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Sets the value of the first export to the value of the first dependency.
     * Equivalent to ({@code set(0, get(0)}).
     *
     * <p>This is a frequently used behaviour. A reference to this method can be passed to {@link
     * Builder#by}.
     */
    public void copyFirst() {
        set(0, get(0));
    }

    // endregion
    // =============================================================================================
    // region [Signaling Errors]
    // =============================================================================================

    /**
     * Used to report that a semantic error occurred during the execution of the rule, which
     * precluded the computation of all of the rule's exported attributes.
     *
     * <p>This is equivalent to calling {@link #errorFor(SemanticError, Attribute...)} with an
     * error using the given description and location (and no cause), and {@link #exports} as last
     * parameter.
     *
     * @param description description of the error
     */
    public void error (String description, Object location) {
        error(new SemanticError(description, null, location));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to report that a semantic error occurred during the execution of the rule, which
     * precluded the computation of all of the rule's exported attributes.
     *
     * <p>This is equivalent to calling {@link #errorFor(SemanticError, Attribute...)} with {@link
     * #exports} as last parameter.
     */
    public void error (SemanticError error)
    {
        if (exports.length == 0) {
            reactor.reportError(error, null);
            return;
        }

        for (int i = 0; i < exports.length; i++) {
            exportValues[i] = error;
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to report that a semantic error occurred during the execution of the rule, which
     * precluded the computation of the given {@code affected} (can be empty).
     *
     * <p>The affected attributes do not need to be exports of the current rule (though that is the
     * most frequent use case). In particular, this freedom is often useful whenever a rule would
     * define a new rule, if not for the error.
     *
     * <p>This is equivalent to calling {@link #errorFor(SemanticError, Attribute...)} with an
     * error using the given description and location (and no cause).
     *
     * @param description description of the error
     */
    public void errorFor (String description, Object location, Attribute... affected) {
        errorFor(new SemanticError(description, null, location), affected);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Used to report that a semantic error occurred during the execution of the rule, which
     * precluded the computation of the given {@code affected} (can be empty).
     *
     * <p>The affected attributes do not need to be exports of the current rule (though that is the
     * most frequent use case). In particular, this freedom is often useful whenever a rule would
     * define a new rule, if not for the error.
     */
    public void errorFor (SemanticError error, Attribute... affected)
    {
        if (affected.length == 0) {
            reactor.reportError(error, null);
            return;
        }

        for (Attribute attr: affected) {
            int index = indexOf(exports, attr);
            if (index >= 0)
                set(index, error);
            else
                // Affected attributes that are not exports must be processed "out of band".
                // Typically those are attribute computed by lazy rules that could not be
                // instantiated.
                reactor.reportError(error, attr);
        }
    }

    // endregion
    // =============================================================================================

    /**
     * Used to build a {@link Rule}. Create an instance by calling {@link Reactor#rule}.
     */
    public static final class Builder
    {
        private final Reactor reactor;
        private final Attribute[] exports;
        private Attribute[] dependencies;
        private Consumer<Rule> computation;

        Builder (Reactor reactor, Attribute... exports) {
            this.reactor = reactor;
            this.exports = exports;
        }

        /**
         * Specifies the dependencies of this rule. (optional)
         */
        public Builder using (Attribute... dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        /**
         * Specifies the dependency of this rule. (optional)
         */
        public Builder using (Object node, String name) {
            return using(NArrays.array(new Attribute(node, name)));
        }

        /**
         * Specifies how to compute the exported attributes from the dependency attributes.
         *
         * <p>This should call {@link Rule#set} to set the value of all exported attributes.
         * Dependency attributes can be accessed through {@link Rule#get}.
         */
        public Void by (Consumer<Rule> computation) {
            if (dependencies == null) dependencies = new Attribute[0];
            reactor.register(new Rule(reactor, exports, dependencies, computation));
            return null;
        }
    }

    // =============================================================================================
}
