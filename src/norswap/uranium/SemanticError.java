package norswap.uranium;

/**
 * Represents an error that occurred during the execution of a {@link Rule}.
 *
 * <p>Ultimately registered with a {@link Reactor}, through {@link Rule#error},
 * {@link Rule#errorFor}, {@link Reactor#error} and their variants.
 */
public final class SemanticError
{
    // ---------------------------------------------------------------------------------------------

    /**
     * English description of the error
     */
    public final String description;

    // ---------------------------------------------------------------------------------------------

    /**
     * If the error was caused by another error, it is available here, otherwise this field is null.
     */
    public final SemanticError cause;

    // ---------------------------------------------------------------------------------------------

    private final Object location;

    // ---------------------------------------------------------------------------------------------

    public SemanticError (String description, SemanticError cause, Object location) {
        this.description = description;
        this.cause = cause;
        this.location = location;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * An object representing the error location, typically an AST node.
     *
     * <p>If the error''s location is null and the error has a casue, will attempt returning the
     * location of its cause instead.
     */
    public Object location() {
        return location != null
            ? location
            : cause != null
                ? cause.location()
                : null;
    }

    // ---------------------------------------------------------------------------------------------

    @Override public String toString() {
        return "SemanticError(" + description + ")";
    }

    // ---------------------------------------------------------------------------------------------
}
