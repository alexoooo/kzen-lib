package tech.kzen.lib.platform.collect

import java.util.IdentityHashMap


actual fun <V> identityMap(): MutableMap<Any, V> {
    return IdentityHashMap()
}
