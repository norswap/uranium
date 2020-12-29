package norswap.uranium;

/**
 * A (Node, String) pair that acts as a handle for an attribute of the node.
 */
public final class Attribute
{
    public final Object node;
    public final String name;

    public Attribute (Object node, String name) {
        this.node = node;
        this.name = name.intern();
    }

    @SuppressWarnings("StringEquality")
    @Override public boolean equals (Object o) {
        if (this == o) return true;
        if (!(o instanceof Attribute)) return false;
        Attribute attribute = (Attribute) o;
        return node == attribute.node && name == attribute.name;
    }

    @Override public int hashCode () {
        return (node == null ? 0 : 31 * node.hashCode()) + name.hashCode();
    }

    @Override public String toString () {
        return "(" + node + " :: " + name + ")";
    }
}
