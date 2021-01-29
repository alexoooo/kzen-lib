package tech.kzen.lib.platform.collect


class PersistentSet<E> private constructor(
    private val delegate: PersistentMap<E, Boolean>
):
    AbstractSet<E>()
{
    //-----------------------------------------------------------------------------------------------------------------
    constructor(): this(persistentMapOf())


    //-----------------------------------------------------------------------------------------------------------------
    override val size: Int
        get() = delegate.size


    override fun contains(element: E): Boolean {
        return delegate.contains(element)
    }


    override fun isEmpty(): Boolean {
        return delegate.isEmpty()
    }


    override fun iterator(): Iterator<E> {
        return delegate.keys.iterator()
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun add(element: E): PersistentSet<E> {
        return PersistentSet(delegate.put(element, true))
    }


    fun addAll(elements: Iterable<E>): PersistentSet<E> {
        var builder = delegate
        for (element in elements) {
            builder = builder.put(element, true)
        }
        return PersistentSet(builder)
    }


    fun remove(element: E): PersistentSet<E> {
        return PersistentSet(delegate.remove(element))
    }


    fun removeAll(elements: Iterable<E>): PersistentSet<E> {
        var builder = delegate
        for (element in elements) {
            builder = builder.remove(element)
        }
        return PersistentSet(builder)
    }
}