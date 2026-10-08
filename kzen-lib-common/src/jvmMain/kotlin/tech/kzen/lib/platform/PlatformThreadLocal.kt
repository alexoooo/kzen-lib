package tech.kzen.lib.platform


actual fun <T> platformThreadLocal(initial: () -> T): () -> T {
    val local = ThreadLocal.withInitial { initial() }
    return { local.get() }
}
