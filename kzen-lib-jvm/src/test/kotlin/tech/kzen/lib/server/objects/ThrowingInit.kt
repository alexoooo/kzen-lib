package tech.kzen.lib.server.objects

import tech.kzen.lib.common.reflect.Reflect


/** Fixture for creation-failure coverage: defines cleanly, blows up at construction time. */
@Reflect
class ThrowingInit {
    init {
        throw IllegalStateException("deliberate construction failure")
    }
}
