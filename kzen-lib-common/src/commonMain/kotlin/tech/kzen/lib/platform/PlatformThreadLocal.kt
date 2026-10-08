package tech.kzen.lib.platform


/**
 * A value per thread where JVM code would use `ThreadLocal`, read through the returned function; each thread's
 * value starts as [initial] (JS is single-threaded, so its actual holds one value).
 */
expect fun <T> platformThreadLocal(initial: () -> T): () -> T
