package tech.kzen.lib.platform


/**
 * Mutual exclusion on [lock] where JVM code would use `synchronized` (JS is single-threaded, so its
 * actual just invokes [block]).
 */
expect fun <R> platformSynchronized(lock: Any, block: () -> R): R
