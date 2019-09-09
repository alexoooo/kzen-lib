package tech.kzen.lib.common.model.resource

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class ResourceContent(
        val value: ByteArray
): Digestible {
    override fun digest(builder: Digest.Builder) {
        builder.addBytes(value)
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ResourceContent

        if (! value.contentEquals(other.value)) return false

        return true
    }


    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}