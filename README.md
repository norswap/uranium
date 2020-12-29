# Uranium

- [Maven Dependency][gh-package] ([mirror][jitpack])
- [Javadoc][snapdoc]

[gh-package]: https://github.com/norswap/uranium/packages/
[jitpack]: https://jitpack.io/#norswap/uranium
[snapdoc]: https://jitpack.io/com/github/norswap/uranium/-SNAPSHOT/javadoc/

Uranium is a semantic analysis framework. It is used to implement the semantic analysis stage of a compiler or
interpreter: typing, building lexical and dynamic scopes, verifying and deriving other properties of interest at
compile time.

Uranium works in a manner reminiscent of [attribute grammars]: it provides support for the computation of *attributes*
on the nodes of an AST (Abstract Syntax Tree) - or really any set of objects, for that matter. These attributes
may be interdependent, and a large part of Uranium's value-added is that it lets you specify these dependencies and
how to compute attribute values from the value of dependencies, then automatically manages the propagation of
dependencies.

[attribute grammars]: https://en.wikipedia.org/wiki/Attribute_grammar

Uranium is the perfect complement to [Autumn], my parsing library.

[Autumn]: https://github.com/norswap/autumn

## Key Concepts

(Rough draft for now.)

- An [`Attribute`] is a (node, name) pair. One of the objective of the framework is to assign values to attributes,
  which may be dependent on the value of other attributes.
  
- The [`Reactor`] is the central workhorse of the framework: it is responsible to compute the value of attributes
  from a set of user-defined specifications.
  
- You can supply basic attribute values directly by using `Reactor#set`.
  
- You can also specify rules ([`Rule`]) to compute attribute values dynamically by using `Reactor#rule`.

- When you have registered all your basic values and rules, you can run the reactor with `Reactor#run`. This
  automatically propagates attribute values and trigger dynamic value computation through the registered rules.
  
- Rules are able to dynamically register new rules. This is sometimes necessary because the node to be used for
  an attribute can only be determined dynamically.
  
- The initial set of values and rules are typically registered during a single-pass tree walk. I recommend using
  the abstract [`Walker`] class from my library [norswap-utils]. Its reflective implementations are particularly
  useful for prototyping.
  
- The other values/rules that can be registered through this pass are computed by `Reactor#run` and are those we
  have called "dynamic" above.
  
- The nature of semantic analysis is to check for potential errors ([`SemanticError`]). Uranium makes it easy to signal
  errors while not disrupting the computational flow. This lets the compiler derive as much information as possible.

- If an error precludes the computation of an attribute value, the error becomes the attribute value. However, that
  value is not propagate to rules depending on the attribute. Instead, the error is propgated: new errors are generated
  for the attributes computed by the dependent rules, signifying they can't be computed because of the original error.
      
- You can inspect the result of a Reactor run by using the [`AttributeTreeFormatter`] class. It is meant for tree-like
  structures and required a [norswap-utils] [`Walker`] to walk the tree.

Be sure to check out the javadoc pages for [`Rule`] and [`Reactor`] which contains **crucial** information on
how to use the framework.

[`Attribute`]: https://javadoc.jitpack.io/com/github/norswap/uranium/-SNAPSHOT/javadoc/norswap/uranium/Attribute.html
[`Rule`]: https://javadoc.jitpack.io/com/github/norswap/uranium/-SNAPSHOT/javadoc/norswap/uranium/Rule.html
[`Reactor`]: https://javadoc.jitpack.io/com/github/norswap/uranium/-SNAPSHOT/javadoc/norswap/uranium/Reactor.html
[`SemanticError`]: https://javadoc.jitpack.io/com/github/norswap/uranium/-SNAPSHOT/javadoc/norswap/uranium/SemanticError.html
[`AttributeTreeFormatter`]: https://javadoc.jitpack.io/com/github/norswap/uranium/-SNAPSHOT/javadoc/norswap/uranium/AttributeTreeFormatter.html

[norswap-utils]: https://github.com/norswap/norswap-utils
[`Walker`]: https://javadoc.jitpack.io/com/github/norswap/norswap-utils/-SNAPSHOT/javadoc/norswap/utils/visitors/Walker.html