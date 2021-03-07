package norswap.uranium;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Holder for multiple {@link SemanticError}.
 *
 * <p><b>Note: currently unused.</b> Keeping this here in case I do introduce a default
 * implementation for an incremental {@link Reactor}.
 */
public final class SemanticErrors
{
    // ---------------------------------------------------------------------------------------------

    private final ArrayList<SemanticError> errors = new ArrayList<>();

    // ---------------------------------------------------------------------------------------------

    void add (SemanticError error) {
        errors.add(error);
    }

    // ---------------------------------------------------------------------------------------------

    public List<SemanticError> errors() {
        return Collections.unmodifiableList(errors);
    }

    // ---------------------------------------------------------------------------------------------

    @Override public String toString()
    {
        String errs = errors.stream()
            .map(e -> "    " + e.toString())
            .collect(Collectors.joining("\n"));

        return "SemanticErrors {" + errs + "\n}";
    }

    // ---------------------------------------------------------------------------------------------
}