package norswap.uranium;

import norswap.utils.TestFixture;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Make your test class inherit this class in order to benefit from its various {@code success} and
 * {@code failure} assertion methods.
 *
 * <p>The idea is that you provide an AST to one of those method, and it will check if that AST
 * passes semantic analysis without errors (or with the specified error for some version of {@code
 * failure}). You need to configure the {@link Reactor} for semantic analysis by implementing the
 * {@link #configureSemanticAnalysis(Reactor, Object)} method.
 *
 * <p>Building ASTs manually can be quite verbose and painful, so you can choose to build them
 * through parsing instead, by implementing the {@link #parse(String)} or {@link #parse(List)}
 * method and then calling the methods starting with {@code successInput} or {@code failureInput}
 * with a string or a list as first parameter.
 *
 * <p>Also see the documentation of the parent class {@link TestFixture}.
 *
 * <h2>Peeling</h2>
 *
 * <p>In particular, whenever an integer {@code peel} parameter is present, it indicates that this
 * many items should be removed from the bottom of the stack trace (outermost/earliest method calls)
 * of the thrown assertion error.
 *
 * <p>All assertion methods take care of peeling themselves off (as only the assertion call site
 * is really interesting), so you do not need to account for them in {@code peel}.
 */
public abstract class UraniumTestFixture extends TestFixture
{
    // ---------------------------------------------------------------------------------------------

    /**
     * Set to {@code false} to avoid warnings when passing a string to {@code success}/{@code
     * failure} (but not {@code successInput}/{@code failureInput}) methods.
     */
    public boolean warnForStringAST = true;

    /**
     * Set to {@code false} to avoid warnings when passing a list to {@code success}/{@code failure}
     * (but not {@code successInput}/{@code failureInput}) methods.
     */
    public boolean warnForListAST = true;

    // ---------------------------------------------------------------------------------------------

    /**
     * Given an AST and a newly created {@link Reactor}, this method configures the reactor
     * by instantiating rules on the AST, so that the reactor can subsequently be run by calling
     * its {@link Reactor#run()} method.
     */
    protected abstract void configureSemanticAnalysis (Reactor reactor, Object ast);

    // ---------------------------------------------------------------------------------------------

    /**
     * Converts an AST node to a string. This string should identity the node to the user without
     * being too verbose. If a file + line/column position is available, it's ideal to include that
     * as well as the node type. Otherwise, an abridged version of the node's source can be useful.
     *
     * <p>See {@link #parse(String)} for a good way to implement this method if you are using
     * the Autumn parsing library.
     */
    protected abstract String astNodeToString (Object node);

    // ---------------------------------------------------------------------------------------------

    /**
     * This method is called by assertion methods taking a {@code String} as first parameter
     * (starting with {@code successInput} and {@code failureInput})  to convert a string input into
     * an abstract syntax tree. It throws an exception by default and must be overriden to be
     * usable.
     *
     * <p>If you are using the Autumn parsing library, here is a good way to implement this method
     * that will also help you debug syntactic errors that you would introduce by inadvertance
     * (we also throw the implementation of {@link #astNodeToString(Object)} for good measure.
     *
     * <pre>
     * private final YourGrammar grammar = new YourGrammar();
     * private final AutumnTestFixture autumnFixture = new AutumnTestFixture();
     *
     * {
     *     autumnFixture.rule = grammar.root();
     *     autumnFixture.runTwice = false;
     *     autumnFixture.bottomClass = this.getClass();
     * }
     *
     * private String input;
     *
     * &#64;Override protected Object parse (String input) {
     *     this.input = input;
     *     return autumnFixture.success(input).topValue();
     * }
     *
     * // Assume `YourNode` is the root node class for `YourGrammar` and it has a `span`
     * // field with type `norswap.autumn.positions.Span`.
     * &#64;Override protected String astNodeToString (Object ast) {
     *     LineMapString map = new LineMapString("<test>", input);
     *     return ast.toString() + " (" + ((YourNode) ast).span.startString(map) + ")";
     * }
     * </pre>
     */
    protected Object parse (String input) {
        throw new UnsupportedOperationException("You need to override the parse(String) method.");
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * This method is called by assertion methods aking a {@code List} as first parameter  (starting
     * with {@code successInput} and {@code failureInput}) tto convert a list of tokens into an
     * abstract syntax tree. It throws an exception by default and must be overriden to be usable.
     *
     * <p>See {@link #parse(String)} for a good way to implement this method if you are using
     * the Autumn parsing library.
     */
    protected Object parse (List<?> input) {
        throw new UnsupportedOperationException("You need to override the parse(List<?>) method.");
    }

    // ---------------------------------------------------------------------------------------------

    private AssertionError assertionError (String message, int peel) {
        AssertionError error = new AssertionError(message);
        trimStackTrace(error, peel + 1);
        return error;
    }

    // ---------------------------------------------------------------------------------------------

    private void astWarnings (Object ast) {
        if (warnForStringAST && ast instanceof String)
            System.out.println("String passed as AST node. This might be okay if you use strings " +
            "in your AST, but it's often a sign you mistakenly called success(...) " +
            "instead of successInput(...). Set the UraniumTestFixture#warnForStringAST " +
            "to false to suppress this warning.");
        if (warnForListAST && ast instanceof List<?>)
            System.out.println("List passed as AST node. This might be okay if you use lists " +
            "in your AST, but it's often a sign you mistakenly called success(...) " +
            "instead of successInput(...). Set the UraniumTestFixture#warnForListAST " +
            "to false to suppress this warning.");
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the AST passes semantic analysis (as configured by {@link
     * #configureSemanticAnalysis(Reactor, Object)}) without errors.
     *
     * @param peel how many calling helper methods to remove from the stack trace (see {@link
     * UraniumTestFixture}, "Peeling" section).
     */
    public void success (Object ast, int peel)
    {
        astWarnings(ast);
        Reactor reactor = new Reactor();
        configureSemanticAnalysis(reactor, ast);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();

        if (!errors.isEmpty()) {
            String report = reactor.reportErrors(this::astNodeToString);
            throw new AssertionError(report);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the AST passes semantic analysis (as configured by {@link
     * #configureSemanticAnalysis(Reactor, Object)}) without errors.
     */
    public void success (Object ast) {
        success(ast, 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(String)}, then checks that the AST
     * passes semantic analysis (as configured by {@link #configureSemanticAnalysis(Reactor,
     * Object)}) without errors.
     */
    public void successInput (String input) {
        success(parse(input));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(List)}, then checks that the AST
     * passes semantic analysis (as configured by {@link #configureSemanticAnalysis(Reactor,
     * Object)}) without errors.
     */
    public void successInput (List<?> input) {
        success(parse(input));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the AST does not pass semantic analysis (as configured by {@link
     * #configureSemanticAnalysis(Reactor, Object)}), i.e. errors are reported.
     */
    public void failure (Object ast)
    {
        astWarnings(ast);
        Reactor reactor = new Reactor();
        configureSemanticAnalysis(reactor, ast);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();
        if (errors.isEmpty())
            throw new AssertionError("Expected errors, but semantic analysis succeeded.");
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(String)}, and checks that the AST
     * does not pass semantic analysis (as configured by {@link #configureSemanticAnalysis(Reactor,
     * Object)}), i.e. errors are reported.
     */
    public void failureInput (String input) {
        failure(parse(input));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(List)}, and checks that the AST
     * does not pass semantic analysis (as configured by {@link #configureSemanticAnalysis(Reactor,
     * Object)}), i.e. errors are reported.
     */
    public void failureInput (List<?> input) {
        failure(parse(input));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * This method underpins the various other {@code failureAt} methods. It behaves like {@link
     * #failureAt(Object, Object...)} when {@code onlyThose} is {@code true}, and like {@link
     * #failureIncludingAt(Object, Object...)} otherwise.

     * <p>The point of this method is to let you write your own test methods that take advantage of
     * the {@code peel} parameter (how many calling helper methods to remove from the stack trace,
     * see {@link TestFixture}, section "Peeling").
     */
    public void failureIncludingAt
            (Object ast, Object[] failureLocationNodes, boolean onlyThose, int peel)
    {
        astWarnings(ast);
        if (failureLocationNodes.length == 0)
            throw new IllegalArgumentException(
                "You did not pass any AST nodes to a `failure...At` function.");

        Reactor reactor = new Reactor();
        configureSemanticAnalysis(reactor, ast);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();
        if (errors.isEmpty())
            throw assertionError("Expected errors, but semantic analysis succeeded.", peel + 1);

        for (Object location: failureLocationNodes)
            if (errors.stream().noneMatch(error -> Objects.equals(error.location(), location)))
                throw assertionError(
                    "No error for location: " + location + "\nActual errors: " + errors,
                    peel + 1);

        if (!onlyThose) return;

        for (SemanticError error: errors)
            if (Arrays.stream(failureLocationNodes)
                    .noneMatch(location -> Objects.equals(error.location(), location)))
                throw assertionError("Unexpected error: " + error, peel + 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the AST does not pass semantic analysis (as configured by {@link
     * #configureSemanticAnalysis(Reactor, Object)}), and in particular that it fails at the given
     * locations (which are AST nodes). The location will be compared using {@link
     * Objects#equals(Object)}.
     *
     * <p>There may be additional errors. If you want to check that errors only occur at the
     * supplied location (which is recommended), use {@link #failureAt(Object, Object...)} instead.
     */
    public void failureIncludingAt (Object ast, Object... failureLocationNodes) {
        failureIncludingAt(ast, failureLocationNodes, false, 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the AST does not pass semantic analysis (as configured by {@link
     * #configureSemanticAnalysis(Reactor, Object)}), and that it fails at the given
     * location (an AST node). The location will be compared using {@link Object#equals(Object)}.
     *
     * <p>If there are errors at other locations than those mentionned, the test will fail.
     * Use {@link #failureIncludingAt(Object, Object...)} if you want to allow these errors
     * (not recommended).
     */
    public void failureAt (Object ast, Object... failureLocationNodes) {
        failureIncludingAt(ast, failureLocationNodes, true, 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * This method underpins the various other {@code failureWith} methods. It behaves like {@link
     * #failureWith(Object, String...)} when {@code onlyThose} is {@code true}, and like {@link
     * #failureIncludingWith(Object, String...)} otherwise.
     *
     * <p>The point of this method is to let you write your own test methods that take advantage
     * of the {@code peel} parameter (see {@link TestFixture}, "Peeling" section).
     *
     *
     */
    public void failureIncludingWith
            (Object ast, String[] descriptionFragments, boolean onlyThose, int peel)
    {
        astWarnings(ast);
        if (descriptionFragments.length == 0)
            throw new IllegalArgumentException(
                "You did not pass any description fragment to a `failure...With` function.");

        Reactor reactor = new Reactor();
        configureSemanticAnalysis(reactor, ast);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();
        if (errors.isEmpty())
            throw assertionError("Expected errors, but semantic analysis succeeded.", peel + 1);

        for (String description: descriptionFragments)
            if (errors.stream().noneMatch(error -> error.description.contains(description)))
                throw assertionError("No error contains description fragment: \"" + description
                        + "\"\nActual errors: " + errors, peel + 1);

        if (!onlyThose) return;

        for (SemanticError error: errors) {
            if (Arrays.stream(descriptionFragments).noneMatch(error.description::contains))
                throw assertionError("Unexpected error: " + error, peel + 1);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the AST does not pass semantic analysis (as configured by {@link
     * #configureSemanticAnalysis(Reactor, Object)}), and in particular that it fails with errors
     * that contain the given strings (an error needs to contain only one of the strings to
     * qualify). This will be determined by using {@link String#contains(CharSequence)} on ({@link
     * SemanticError#description}.
     *
     * <p>There may be additional errors. If you want to check that only errors with the given
     * descriptions happen (which is recommended), use {@link #failureWith(Object, String...)}
     * instead.
     */
    public void failureIncludingWith (Object ast, String... descriptionFragments) {
        failureIncludingWith(ast, descriptionFragments, false, 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Checks that the AST does not pass semantic analysis (as configured by {@link
     * #configureSemanticAnalysis(Reactor, Object)}), and in particular that it fails with errors
     * that contain the given strings (an error needs to contain only one of the strings to
     * qualify). This will be determined by using {@link String#contains(CharSequence)} on ({@link
     * SemanticError#description}.
     *
     * <p>If there are errors with other descriptions than those mentionned, the test will fail. Use
     * {@link #failureIncludingWith(Object, String...)} if you want to allow these errors (not
     * recommended).
     */
    public void failureWith (Object ast, String... descriptionFragments) {
        failureIncludingWith(ast, descriptionFragments, true, 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(String)}, then proceeds
     * exactly like {@link #failureIncludingWith(Object, String...)}.
     */
    public void failureInputIncludingWith (String input, String... descriptionFragments) {
        failureIncludingWith(parse(input), descriptionFragments, false, 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(String)}, then proceeds
     * exactly like {@link #failureWith(Object, String...)}.
     */
    public void failureInputWith (String input, String... descriptionFragments) {
        failureIncludingWith(parse(input), descriptionFragments, true, 1);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(List)}, then proceeds
     * exactly like {@link #failureIncludingWith(Object, String...)}.
     */
    public void failureInputIncludingWith (List<?> input, String... descriptionFragments) {
        failureIncludingWith(parse(input), descriptionFragments);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Constructs an AST for the given input using {@link #parse(List)}, then proceeds
     * exactly like {@link #failureWith(Object, String...)}.
     */
    public void failureInputWith (List<?> input, String... descriptionFragments) {
        failureWith(parse(input), descriptionFragments);
    }

    // ---------------------------------------------------------------------------------------------
}
