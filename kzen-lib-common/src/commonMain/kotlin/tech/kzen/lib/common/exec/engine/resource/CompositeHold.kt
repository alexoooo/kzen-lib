package tech.kzen.lib.common.exec.engine.resource


/**
 * One hold over several: active while any member is, released together in list order — every member is released
 * even when one of them fails to close, with the first close failure thrown and the rest suppressed.
 */
internal class CompositeHold(
    private val holds: List<Hold>
): Hold {
    override val isActive: Boolean
        get() = holds.any { it.isActive }


    override fun release() {
        Hold.releaseAll(holds)
    }
}
