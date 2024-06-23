package tech.kzen.lib.platform.collect


// NB: can't use https://github.com/Kotlin/kotlinx.collections.immutable because it's jvm-only
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class PersistentList<out E>(): List<E>, RandomAccess
{
    //-----------------------------------------------------------------------------------------------------------------
    fun add(element: @UnsafeVariance E): PersistentList<E>

    fun add(index: Int, element: @UnsafeVariance E): PersistentList<E>

    fun addAll(elements: Iterable<@UnsafeVariance E>): PersistentList<E>

    fun addAll(index: Int, elements: Iterable<@UnsafeVariance E>): PersistentList<E>

    fun set(index: Int, element: @UnsafeVariance E): PersistentList<E>

    fun removeAt(index: Int): PersistentList<E>

    // NB: from inclusive, to exclusive (can't change their name here without triggering warning)
    override fun subList(fromIndex: Int, toIndex: Int): PersistentList<E>


    //-----------------------------------------------------------------------------------------------------------------
    // NB: re-declared from List, not sure why that's necessary (Kotlin 2.0.0 / Gradle 8.8)
    override operator fun get(index: Int): E
    override fun contains(element: @UnsafeVariance E): Boolean
    override fun containsAll(elements: Collection<@UnsafeVariance E>): Boolean
    override fun indexOf(element: @UnsafeVariance E): Int
    override fun isEmpty(): Boolean
    override fun iterator(): Iterator<E>
    override fun lastIndexOf(element: @UnsafeVariance E): Int
    override fun listIterator(): ListIterator<E>
    override fun listIterator(index: Int): ListIterator<E>
    override val size: Int
}