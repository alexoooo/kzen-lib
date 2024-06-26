package tech.kzen.lib.platform.collect

import com.github.andrewoma.dexx.collection.HashMap
import com.github.andrewoma.dexx.collection.TreeMap
import com.google.common.collect.Iterators
import com.google.common.collect.Maps


// https://stackoverflow.com/a/9313962/1941359
// https://youtu.be/lcI-jmh5Cf0
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")
actual class PersistentMap<K, out V> private constructor(
    private val delegate: HashMap<K, Pair<V, Long>>,
    private val orderDelegate: TreeMap<Long, K>,
    private val insertCount: Long
):
    Map<K, V>,
    AbstractMap<K, V>()
{
    //-----------------------------------------------------------------------------------------------------------------
    actual constructor(): this(
        HashMap.empty<K, Pair<V, Long>>(),
        TreeMap<Long, K>(),
        0
    )


    //-----------------------------------------------------------------------------------------------------------------
    actual override val entries: Set<Map.Entry<K, V>>
        get() {
            return object: AbstractSet<Map.Entry<K, V>>() {
                override val size: Int
                    get() = delegate.size()

                override fun iterator(): Iterator<Map.Entry<K, V>> {
                    return object: Iterator<Map.Entry<K, V>> {
                        private val orderIterator = orderDelegate.values().iterator()

                        override fun hasNext(): Boolean {
                            return orderIterator.hasNext()
                        }

                        override fun next(): Map.Entry<K, V> {
                            val nextKey = orderIterator.next()!!
                            val nextValue = delegate[nextKey]!!
                            return Maps.immutableEntry(nextKey, nextValue.first)
                        }
                    }
                }
            }
        }


    //-----------------------------------------------------------------------------------------------------------------
    actual override val size: Int
        get() = delegate.size()


    actual override fun containsKey(key: K): Boolean {
        return delegate.containsKey(key!!)
    }


    actual override operator fun get(key: K): V? {
        return delegate.get(key!!)?.first
    }


    actual override val keys: Set<K>
        get() {
            return object: AbstractSet<K>() {
                override val size: Int
                    get() = delegate.size()

                override fun iterator(): Iterator<K> {
                    return orderDelegate.values().iterator()
                }

                override fun contains(element: K): Boolean {
                    return containsKey(element)
                }
            }
        }


    actual override val values: Collection<V>
        get() {
            return object: AbstractCollection<V>() {
                override val size: Int
                    get() = delegate.size()

                override fun iterator(): Iterator<V> {
                    return Iterators.transform(orderDelegate.values().iterator()) {
                        delegate[it!!]!!.first
                    }
                }
            }
        }


    //-----------------------------------------------------------------------------------------------------------------
    actual fun put(key: K, value: @UnsafeVariance V): PersistentMap<K, V> {
        val existing = delegate[key!!]

        return if (existing == null) {
            PersistentMap(
                    delegate.put(key, value to insertCount),
                    orderDelegate.put(insertCount, key),
                    insertCount + 1
            )
        }
        else {
            PersistentMap(
                    delegate.put(key, value to existing.second),
                    orderDelegate,
                    insertCount)
        }
    }


    actual fun putAll(from: Map<K, @UnsafeVariance V>): PersistentMap<K, V> {
        var buffer = this
        for (e in from) {
            buffer = buffer.put(e.key, e.value)
        }
        return buffer
    }


    actual fun remove(key: K): PersistentMap<K, V> {
        val existing = delegate[key!!]
                ?: return this

        return PersistentMap(
                delegate.remove(key),
                orderDelegate.remove(existing.second),
                insertCount)
    }


    actual fun insert(
        key: K,
        value: @UnsafeVariance V,
        position: Int
    ): PersistentMap<K, V> {
        check(key !in this)

        if (position == size) {
            return put(key, value)
        }

        var builder = PersistentMap<K, V>()
        val iterator = orderDelegate.values().iterator()

        var nextIndex = 0
        while (true) {
            if (nextIndex == position) {
                break
            }
            nextIndex++

            val nextKey = iterator.next()!!
            val nextValue = delegate.get(nextKey)!!
            builder = builder.put(nextKey, nextValue.first)
        }

        builder = builder.put(key, value)

        while (iterator.hasNext()) {
            val nextKey = iterator.next()!!
            val nextValue = delegate.get(nextKey)!!
            builder = builder.put(nextKey, nextValue.first)
        }

        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual fun equalsInOrder(other: PersistentMap<K, @UnsafeVariance V>): Boolean {
        if (size != other.size) {
            return false
        }

        val orderIterator = orderDelegate.values().iterator()
        val otherOrderIterator = other.orderDelegate.values().iterator()

        while (orderIterator.hasNext()) {
            val nextKey = orderIterator.next()
            val otherNextKey = otherOrderIterator.next()

            if (nextKey != otherNextKey ||
                    get(nextKey) != other[nextKey]) {
                return false
            }
        }

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual override fun containsValue(value: @UnsafeVariance V): Boolean {
        return delegate.values().any { it.first == value }
    }
}