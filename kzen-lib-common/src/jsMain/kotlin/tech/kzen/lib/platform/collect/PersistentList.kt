package tech.kzen.lib.platform.collect

import tech.kzen.lib.platform.wrap.ImmutableList


actual class PersistentList<out E> private constructor(
        private val delegate: ImmutableList<@UnsafeVariance E>
):
        List<E>,
        AbstractList<E>(),
        RandomAccess
{
    //-----------------------------------------------------------------------------------------------------------------
    actual constructor(): this(ImmutableList())


    //-----------------------------------------------------------------------------------------------------------------
    override val size: Int
        get() = delegate.size


    override fun get(index: Int): E {
        return delegate.get(index)
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual fun add(element: @UnsafeVariance E): PersistentList<E> {
        return PersistentList(
                delegate.push(element))
    }


    actual fun add(index: Int, element: @UnsafeVariance E): PersistentList<E> {
        return PersistentList(
                delegate.insert(index, element))
    }


    actual fun addAll(elements: Iterable<@UnsafeVariance E>): PersistentList<E> {
        var builder = delegate
        for (i in elements) {
            builder = builder.push(i)
        }
        return PersistentList(builder)
    }


    actual fun set(index: Int, element: @UnsafeVariance E): PersistentList<E> {
        return PersistentList(
                delegate.set(index, element))
    }


    actual fun removeAt(index: Int): PersistentList<E> {
        return PersistentList(
                delegate.remove(index))
    }


    actual override fun subList(fromIndex: Int, toIndex: Int): PersistentList<E> {
        return PersistentList(
                delegate.slice(fromIndex, toIndex))
    }
}