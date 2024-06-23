package tech.kzen.lib.platform.collect


// Ordering:
// - iteration in same order as keys were inserted
// - replacing existing value maintains order
// - equals / hashCode are unordered, see equalsInOrder
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PersistentMap<K, out V>(): Map<K, V> {
    //-----------------------------------------------------------------------------------------------------------------
    fun put(key: K, value: @UnsafeVariance V): PersistentMap<K, V>

    fun putAll(from: Map<K, @UnsafeVariance V>): PersistentMap<K, V>

    fun remove(key: K): PersistentMap<K, V>

    fun insert(key: K, value: @UnsafeVariance V, position: Int): PersistentMap<K, V>

    fun equalsInOrder(other: PersistentMap<K, @UnsafeVariance V>): Boolean


    //-----------------------------------------------------------------------------------------------------------------
    // NB: re-declared from List, not sure why that's necessary (Kotlin 2.0.0 / Gradle 8.8)
    override fun containsKey(key: K): Boolean
    override fun containsValue(value: @UnsafeVariance V): Boolean
    override operator fun get(key: K): V?
    override fun isEmpty(): Boolean
    override val entries: Set<Map.Entry<K, V>>
    override val keys: Set<K>
    override val size: Int
    override val values: Collection<V>
}