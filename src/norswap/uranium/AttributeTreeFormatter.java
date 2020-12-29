package norswap.uranium;

import norswap.utils.Strings;
import norswap.utils.visitors.WalkVisitType;
import norswap.utils.visitors.Walker;

/**
 * See {@link #format(Object, Reactor, Walker)}.
 */
public final class AttributeTreeFormatter<Node>
{
    // ---------------------------------------------------------------------------------------------

    private static final int INDENT = 2;

    // ---------------------------------------------------------------------------------------------

    private final Reactor reactor;
    private final Walker<Node> walker;
    private final StringBuilder b = new StringBuilder();
    private int indent = 0;

    // ---------------------------------------------------------------------------------------------

    private AttributeTreeFormatter (Reactor reactor, Walker<Node> walker) {
        this.reactor = reactor;
        this.walker = walker;
        walker.register_fallback(WalkVisitType.PRE_VISIT,  this::preVisit);
        walker.register_fallback(WalkVisitType.POST_VISIT, this::postVisit);
    }

    // ---------------------------------------------------------------------------------------------

    private void preVisit (Node node) {
        b.append(Strings.repeat(' ', indent));
        b.append(node);
        indent += INDENT;

        reactor.getAll(node).forEach(p -> {
            b.append("\n");
            b.append(Strings.repeat(' ', indent));
            b.append(":: ");
            b.append(p.getKey().name);
            b.append(" = ");
            b.append(p.getValue());
        });

        b.append("\n");
    }

    // ---------------------------------------------------------------------------------------------

    private void postVisit (Node node) {
        indent -= INDENT;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns a string holding a tree-like (= indented children) view of the AST, where all
     * attribute computed by the reactor for an AST node are enumerated after printing the node,
     * before listing its descendants, which are similarly formatted.
     */
    public static <Node> String format (Node root, Reactor reactor, Walker<Node> walker)
    {
        AttributeTreeFormatter<Node> formatter = new AttributeTreeFormatter<>(reactor, walker);
        formatter.walker.walk(root);
        return formatter.b.toString();
    }

    // ---------------------------------------------------------------------------------------------
}
