package tech.kzen.lib.platform.collect


// A Kotlin/JS object's default hashCode / equals are identity-based, which is what an identity map needs;
// a key class that overrides them is not identity-keyed here.
actual fun <V> identityMap(): MutableMap<Any, V> {
    return HashMap()
}
