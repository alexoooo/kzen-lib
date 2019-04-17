package tech.kzen.lib.platform.collect


// NB: iteration in same order as keys were inserted
expect class PersistentMap<K, out V>(): Map<K, V> {
    fun put(key: K, value: @UnsafeVariance V): PersistentMap<K, V>

//    fun putAll(from: Map<K, @UnsafeVariance V>): PersistentMap<K, V>

    fun remove(key: K): PersistentMap<K, V>

    fun insert(key: K, value: @UnsafeVariance V, position: Int): PersistentMap<K, V>
}