package tech.kzen.lib.server.objects

import tech.kzen.lib.common.reflect.Reflect


class NestedObject {
    @Reflect
    class Nested(
            val foo: Int
    )
}