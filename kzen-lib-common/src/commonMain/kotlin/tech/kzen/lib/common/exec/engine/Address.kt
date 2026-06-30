package tech.kzen.lib.common.exec.engine


/**
 * A within-node address for a live trace value (logic-spec §7 latest-value-per-address). A node's live
 * state is a map of [Address] to value; nested sub-values use child addresses. The node itself is
 * identified separately by [NodeId] / stable id, so this is purely the *intra-node* path.
 */
data class Address(
    val segments: List<String>
) {
    companion object {
        val root = Address(listOf())

        fun of(vararg segments: String): Address =
            Address(segments.toList())
    }

    fun child(segment: String): Address =
        Address(segments + segment)

    override fun toString(): String =
        segments.joinToString("/")
}
