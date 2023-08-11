package tech.kzen.lib.server.objects.nested.user

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.server.objects.nested.NestedObject


@Suppress("RemoveRedundantQualifierName", "RemoveEmptyPrimaryConstructor")
class NestedUser() {
    @Suppress("unused")
    class PriorClass(
            var bar: NestedUser.Nested
    )


    @Reflect
    class Nested(
            @Suppress("UNUSED_PARAMETER") objectLocation: ObjectLocation,
            private val delegate: NestedObject.Nested
    ): NestedObject.Foo {
        override fun foo(): Int =
                delegate.foo()
    }


    @Reflect
    class Nested2(
            private val delegate: Nested
    ): NestedObject.Foo {
        override fun foo(): Int =
                delegate.foo()
    }
}