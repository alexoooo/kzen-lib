package tech.kzen.lib.common.exec.engine.resource


/**
 * Who holds a resource: an execution frame, a channel, a Worker's in-flight callback, an accumulator's explicit
 * retention. A name, so the live view can say *who* keeps a resource open — a notation location rather than an
 * instance, so a live-edit replacement is the same holder.
 */
data class Holder(
    val name: String
) {
    override fun toString(): String = name
}
