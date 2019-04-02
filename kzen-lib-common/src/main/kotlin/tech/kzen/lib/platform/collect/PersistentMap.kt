package tech.kzen.lib.platform.collect


interface PersistentMap<K, out V>: Map<K, V> {
    fun put(key: K, value: @UnsafeVariance V): PersistentMap<K, V>

    fun remove(key: K): PersistentMap<K, V>
}