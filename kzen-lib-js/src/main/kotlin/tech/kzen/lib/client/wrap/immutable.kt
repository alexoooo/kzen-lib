@file:JsModule("immutable")
@file:JsNonModule
package tech.kzen.lib.client.wrap


@JsName("List")
external class ImmutableList<E> {
    val size: Int
    fun get(index: Int): E
    fun push(element: E): ImmutableList<E>
    fun insert(index: Int, element: E): ImmutableList<E>
    fun set(index: Int, element: E): ImmutableList<E>
    fun remove(index: Int): ImmutableList<E>
    fun slice(begin: Int, end: Int): ImmutableList<E>
}


@JsName("Map")
external class ImmutableMap<K, out V> {
    val size: Int
    fun has(key: K): Boolean
    fun get(key: K, notSetValue: @UnsafeVariance V?): V?
    fun set(key: K, value: @UnsafeVariance V): ImmutableMap<K, V>
    fun delete(key: K): ImmutableMap<K, V>
    fun keys(): IterationIterator<K>
    fun values(): IterationIterator<@UnsafeVariance V>
    fun entries(): IterationIterator<Array<Any>>
}

@JsName("OrderedMap")
external class ImmutableOrderedMap<K, out V> {
    val size: Int
    fun has(key: K): Boolean
    fun get(key: K, notSetValue: @UnsafeVariance V?): V?
    fun set(key: K, value: @UnsafeVariance V): ImmutableOrderedMap<K, V>
    fun delete(key: K): ImmutableOrderedMap<K, V>
    fun keys(): IterationIterator<K>
    fun values(): IterationIterator<@UnsafeVariance V>
    fun entries(): IterationIterator<Array<Any>>
}


external interface IterationIterator<T> {
    fun next(): IteratorResult<T>
}

external interface IteratorResult<T> {
    val done: Boolean
    val value: T
}