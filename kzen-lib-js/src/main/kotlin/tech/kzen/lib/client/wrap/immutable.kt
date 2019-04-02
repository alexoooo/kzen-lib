@file:JsModule("immutable")
@file:JsNonModule
package tech.kzen.lib.client.wrap


@JsName("List")
external class ImmutableList<E> {
    val size: Int
    fun get(index: Int): E
    fun push(element: E): ImmutableList<E>
}