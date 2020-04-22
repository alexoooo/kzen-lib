package tech.kzen.lib.server.objects.nested

import tech.kzen.lib.common.reflect.Reflect


class NestedObject {
    interface Foo {
        fun foo(): Int
    }

    @Reflect
    class Nested(
            // test annotation
            @Suppress("unused")
            val foo: Int
    ): Foo {
        override fun foo(): Int =
                foo
    }


    @Reflect
    data class Nested2<T>(
            val foo: List<T>
    )
}