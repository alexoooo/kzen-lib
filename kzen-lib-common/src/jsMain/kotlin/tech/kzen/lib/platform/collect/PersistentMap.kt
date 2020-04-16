package tech.kzen.lib.platform.collect

import tech.kzen.lib.platform.wrap.ImmutableOrderedMap
import tech.kzen.lib.platform.wrap.IteratorResult


actual class PersistentMap<K, out V> private constructor(
        private val delegate: ImmutableOrderedMap<K, V>
):
        Map<K, V>,
        AbstractMap<K, V>()
{
    //-----------------------------------------------------------------------------------------------------------------
    actual constructor(): this(ImmutableOrderedMap())


    //-----------------------------------------------------------------------------------------------------------------
    override val entries: Set<Map.Entry<K, V>>
        get() {
            return object: AbstractSet<Map.Entry<K, V>>() {
                override val size: Int
                    get() = delegate.size

                override fun iterator(): Iterator<Map.Entry<K, V>> {
                    return object: Iterator<Map.Entry<K, V>> {
                        private val delegateIterator = delegate.entries()
                        private var result: IteratorResult<Array<Any>>? = null

                        override fun hasNext(): Boolean {
                            if (result == null) {
                                result = delegateIterator.next()
                            }
                            return ! result!!.done
                        }

                        @Suppress("UNCHECKED_CAST")
                        override fun next(): Map.Entry<K, V> {
                            check(hasNext())
                            val next = result!!.value
                            result = null
                            return object: Map.Entry<K, V> {
                                override val key: K
                                    get() = next[0] as K

                                override val value: V
                                    get() = next[1] as V
                            }
                        }
                    }
                }
            }
        }


    //-----------------------------------------------------------------------------------------------------------------
    override val size: Int
        get() = delegate.size


    override fun containsKey(key: K): Boolean {
        return delegate.has(key)
    }


    override operator fun get(key: K): V? {
        return delegate.get(key, null)
    }


    override val keys: Set<K>
        get() {
            return object: AbstractSet<K>() {
                override val size: Int
                    get() = delegate.size

                override fun iterator(): Iterator<K> {
                    return object: Iterator<K> {
                        private val delegateIterator = delegate.keys()
                        private var result: IteratorResult<K>? = null

                        override fun hasNext(): Boolean {
                            if (result == null) {
                                result = delegateIterator.next()
                            }
                            return ! result!!.done
                        }

                        @Suppress("UNCHECKED_CAST")
                        override fun next(): K {
                            check(hasNext())
                            val next = result!!.value
                            result = null
                            return next
                        }
                    }
                }

                override fun contains(element: K): Boolean {
                    return containsKey(element)
                }
            }
        }


    override val values: Collection<V>
        get() {
            return object: AbstractCollection<V>() {
                override val size: Int
                    get() = delegate.size

                override fun iterator(): Iterator<V> {
                    return object: Iterator<V> {
                        private val delegateIterator = delegate.values()
                        private var result: IteratorResult<V>? = null

                        override fun hasNext(): Boolean {
                            if (result == null) {
                                result = delegateIterator.next()
                            }
                            return ! result!!.done
                        }

                        @Suppress("UNCHECKED_CAST")
                        override fun next(): V {
                            check(hasNext())
                            val next = result!!.value
                            result = null
                            return next
                        }
                    }
                }
            }
        }


    //-----------------------------------------------------------------------------------------------------------------
    actual fun put(key: K, value: @UnsafeVariance V): PersistentMap<K, V> {
        return PersistentMap(delegate.set(key, value))
    }


    actual fun putAll(from: Map<K, @UnsafeVariance V>): PersistentMap<K, V> {
        var buffer = this
        for (e in from) {
            buffer = buffer.put(e.key, e.value)
        }
        return buffer
    }


    actual fun remove(key: K): PersistentMap<K, V> {
        return PersistentMap(delegate.delete(key))
    }


    @Suppress("UNCHECKED_CAST")
    actual fun insert(key: K, value: @UnsafeVariance V, position: Int): PersistentMap<K, V> {
        check(key !in this)

        if (position == size) {
            return put(key, value)
        }

        var builder = PersistentMap<K, V>()
        val iterator = delegate.entries()

        var result: IteratorResult<Array<Any>> = iterator.next()

        var nextIndex = 0
        while (true) {
            if (nextIndex == position) {
                break
            }
            nextIndex++

            val entryKey = result.value[0] as K
            val entryValue = result.value[1] as V

            builder = builder.put(entryKey, entryValue)

            result = iterator.next()
        }

        builder = builder.put(key, value)

        while (! result.done) {
            val entryKey = result.value[0] as K
            val entryValue = result.value[1] as V

            builder = builder.put(entryKey, entryValue)

            result = iterator.next()
        }

        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual fun equalsInOrder(other: PersistentMap<K, @UnsafeVariance V>): Boolean {
        if (size != other.size) {
            return false
        }

        val keyIterator = keys.iterator()
        val otherKeyIterator = other.keys.iterator()

        while (keyIterator.hasNext()) {
            val key = keyIterator.next()
            val otherKey = otherKeyIterator.next()

            if (key != otherKey ||
                    get(key) != other[key]) {
                return false
            }
        }

        return true
    }
}