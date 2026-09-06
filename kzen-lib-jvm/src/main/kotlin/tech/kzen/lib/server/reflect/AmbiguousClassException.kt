package tech.kzen.lib.server.reflect


/**
 * A class name defined by more than one plugin scope: a resolution-time error naming every defining scope,
 * never a first-wins choice. A [ClassNotFoundException] so ordinary loading code sees a load failure, while
 * [ReflectiveClassMirror] reports it as a malformed (named) entry rather than an absent class.
 */
class AmbiguousClassException(
    val className: String,
    val definingScopes: List<String>
): ClassNotFoundException(
    "Class $className is defined by ${definingScopes.size} plugin scopes: ${definingScopes.joinToString()}"
)
