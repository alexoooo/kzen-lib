package tech.kzen.lib.platform.collect


// NB: can't use https://github.com/Kotlin/kotlinx.collections.immutable because it's jvm-only
expect class PersistentList<out E>(): List<E>, RandomAccess
{
    fun add(element: @UnsafeVariance E): PersistentList<E>

    fun add(index: Int, element: @UnsafeVariance E): PersistentList<E>

    fun addAll(elements: Iterable<@UnsafeVariance E>): PersistentList<E>

    fun set(index: Int, element: @UnsafeVariance E): PersistentList<E>

    fun removeAt(index: Int): PersistentList<E>

    // NB: from inclusive, to exclusive (can't change their name here without triggering warning)
    override fun subList(fromIndex: Int, toIndex: Int): PersistentList<E>
}