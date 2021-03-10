package norswap.uranium;

import norswap.utils.Strings;
import norswap.utils.visitors.ReflectiveAccessorWalker;
import norswap.utils.visitors.ReflectiveFieldWalker;
import norswap.utils.visitors.WalkVisitType;
import norswap.utils.visitors.Walker;

import java.util.Collection;

import static norswap.utils.visitors.WalkVisitType.POST_VISIT;
import static norswap.utils.visitors.WalkVisitType.PRE_VISIT;

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
        walker.registerFallback(PRE_VISIT,  this::preVisit);
        walker.registerFallback(POST_VISIT, this::postVisit);
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
     *
     * @param walker is used to walk the tree while creating the string. It must support
     *               {@link WalkVisitType#PRE_VISIT} and {@link WalkVisitType#POST_VISIT}.
     */
    public static <Node> String format (Node root, Reactor reactor, Walker<Node> walker)
    {
        AttributeTreeFormatter<Node> formatter = new AttributeTreeFormatter<>(reactor, walker);
        formatter.walker.walk(root);
        return formatter.b.toString();
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Just like {@link #format(Object, Reactor, Walker)} but automatically constructs a {@link
     * ReflectiveFieldWalker} for the given class, which can traverse all fields compatible with the
     * given class or a {@link Collection} thereof in the {@code Node} class' subclasses.
     */
    public static <Node> String formatWalkFields (Node root, Reactor reactor, Class<Node> klass) {
        return format(root, reactor, new ReflectiveFieldWalker<>(klass, PRE_VISIT, POST_VISIT));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Just like {@link #format(Object, Reactor, Walker)} but automatically constructs a {@link
     * ReflectiveAccessorWalker} for the given class, which can traverse all accessors (no-argument
     * methods) whose return type is compatible with the given class or a {@link Collection}
     * thereof, in the {@code Node} class' subclasses.
     */
    public static <Node> String formatWalkMethods (Node root, Reactor reactor, Class<Node> klass) {
        return format(root, reactor, new ReflectiveAccessorWalker<>(klass, PRE_VISIT, POST_VISIT));
    }

    // ---------------------------------------------------------------------------------------------
}
