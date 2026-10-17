package tech.kzen.lib.platform.collect


/** A mutable map keyed by object identity (reference equality), where the JVM would use `IdentityHashMap`. */
expect fun <V> identityMap(): MutableMap<Any, V>
