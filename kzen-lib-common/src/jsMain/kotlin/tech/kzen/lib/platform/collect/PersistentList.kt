package tech.kzen.lib.platform.collect

import tech.kzen.lib.platform.wrap.ImmutableList


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
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
    actual override val size: Int
        get() = delegate.size


    actual override fun get(index: Int): E {
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

    actual fun addAll(
        index: Int,
        elements: Iterable<@UnsafeVariance E>
    ): PersistentList<E> {
        var builder: ImmutableList<E> = delegate.slice(0, index)

        for (addend in elements) {
            builder = builder.push(addend)
        }

        for (i in index until size) {
            builder = builder.push(get(i))
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


    //-----------------------------------------------------------------------------------------------------------------
    actual override fun contains(element: @UnsafeVariance E): Boolean {
        return delegate.includes(element)
    }

    actual override fun indexOf(element: @UnsafeVariance E): Int {
        return delegate.indexOf(element)
    }

    actual override fun lastIndexOf(element: @UnsafeVariance E): Int {
        return delegate.lastIndexOf(element)
    }
}