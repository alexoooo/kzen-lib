package tech.kzen.lib.platform.collect

import tech.kzen.lib.client.wrap.ImmutableList


actual class PersistentList<out E> private constructor(
        private val delegate: ImmutableList<@UnsafeVariance E>
):
        List<E>,
        AbstractList<E>()
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