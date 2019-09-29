package tech.kzen.lib.platform.collect


// Ordering:
// - iteration in same order as keys were inserted
// - replacing existing value maintains order
// - equals / hashCode are unordered, see equalsInOrder
expect class PersistentMap<K, out V>(): Map<K, V> {
    fun put(key: K, value: @UnsafeVariance V): PersistentMap<K, V>

//    fun putAll(from: Map<K, @UnsafeVariance V>): PersistentMap<K, V>

    fun remove(key: K): PersistentMap<K, V>

    fun insert(key: K, value: @UnsafeVariance V, position: Int): PersistentMap<K, V>

    fun equalsInOrder(other: PersistentMap<K, @UnsafeVariance V>): Boolean
}