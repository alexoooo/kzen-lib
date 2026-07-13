package tech.kzen.lib.platform


actual fun <R> platformSynchronized(lock: Any, block: () -> R): R {
    return block()
}
