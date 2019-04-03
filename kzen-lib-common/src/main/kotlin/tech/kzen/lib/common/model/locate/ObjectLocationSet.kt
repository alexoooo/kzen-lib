package tech.kzen.lib.common.model.locate


data class ObjectLocationSet(
        val values: Set<ObjectLocation>
) {
    companion object {
        val empty = ObjectLocationSet(setOf())
    }
}