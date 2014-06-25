javapeg
=======

Simple top-down recursive PEG parser for java.

The main idea behind javapeg is to provide a simple library with the following restrictions:

  * It shall not use annotations, nor require a special maven plugin, nor any java agent.
  * There shall be no lexer, as PEGs don't really need them.
  * The API should be reasonably small (unless you want to do weird stuff).
  * It shall work with no more than java 5, not depending on any external library.
  * There shall be easy support for just obtaining the AST, that should be the default in fact.

That's why:

  * Only PEGs are supported.
  * The input must be a String (fully known before parsing).
  * Grammars can be specified by manually instantiating any of the Grammar child classes, or just by specifying the PEG
    grammar as a simple string on run-time, using standard PEG syntax.
