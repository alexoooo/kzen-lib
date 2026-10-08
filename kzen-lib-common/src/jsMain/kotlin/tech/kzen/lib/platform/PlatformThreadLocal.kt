package tech.kzen.lib.platform


actual fun <T> platformThreadLocal(initial: () -> T): () -> T {
    val value = initial()
    return { value }
}
