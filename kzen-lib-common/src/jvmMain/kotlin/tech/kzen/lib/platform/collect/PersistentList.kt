package tech.kzen.lib.platform.collect

import com.github.andrewoma.dexx.collection.Vector


// TODO: investigate using https://github.com/lacuna/bifurcan
actual class PersistentList<out E> private constructor(
        private val delegate: Vector<@UnsafeVariance E>
):
        List<E>,
        AbstractList<E>(),
        RandomAccess
{
    //-----------------------------------------------------------------------------------------------------------------
    actual constructor() : this(Vector.empty<E>())


    //-----------------------------------------------------------------------------------------------------------------
    override val size: Int
        get() = delegate.size()


    override fun get(index: Int): E {
        return delegate.get(index)
    }


    //-----------------------------------------------------------------------------------------------------------------
    actual fun add(element: @UnsafeVariance E): PersistentList<E> {
        return PersistentList(delegate.append(element))
    }


    actual fun add(index: Int, element: @UnsafeVariance E): PersistentList<E> {
        var builder: Vector<E> = delegate.take(index)

        builder = builder.append(element)

        for (i in index until size) {
            builder = builder.append(get(i))
        }

        return PersistentList(builder)
    }


    actual fun addAll(elements: Iterable<@UnsafeVariance E>): PersistentList<E> {
        var builder = delegate
        for (i in elements) {
            builder = builder.append(i)
        }
        return PersistentList(builder)
    }


    actual fun addAll(
        index: Int,
        elements: Iterable<@UnsafeVariance E>
    ): PersistentList<E> {
        var builder: Vector<E> = delegate.take(index)

        for (addend in elements) {
            builder = builder.append(addend)
        }

        for (i in index until size) {
            builder = builder.append(get(i))
        }

        return PersistentList(builder)
    }


    actual fun set(index: Int, element: @UnsafeVariance E): PersistentList<E> {
        return PersistentList(
                delegate.set(index, element))
    }


    actual fun removeAt(index: Int): PersistentList<E> {
        if (index == 0) {
            return PersistentList(delegate.drop(1))
        }

        // https://groups.google.com/forum/#!topic/scala-user/fZ1TTNgneW4
        // https://lacuna.io/docs/bifurcan/io/lacuna/bifurcan/List.html#concat-io.lacuna.bifurcan.IList-

        var builder: Vector<E> = delegate.take(index)
        for (i in index + 1 until size) {
            if (i == index) {
                continue
            }
            builder = builder.append(get(i))
        }
        return PersistentList(builder)
    }


    actual override fun subList(fromIndex: Int, toIndex: Int): PersistentList<E> {
        return PersistentList(
                delegate.range(fromIndex, true, toIndex, false))
    }
}