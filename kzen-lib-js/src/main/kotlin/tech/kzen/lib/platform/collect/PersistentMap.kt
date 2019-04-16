package tech.kzen.lib.platform.collect

import tech.kzen.lib.client.wrap.ImmutableOrderedMap
import tech.kzen.lib.client.wrap.IteratorResult


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


    actual fun remove(key: K): PersistentMap<K, V> {
        return PersistentMap(delegate.delete(key))
    }
}