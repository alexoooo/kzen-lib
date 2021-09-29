package tech.kzen.lib.common.util


class ImmutableByteArray private constructor(
        internal val bytes: ByteArray
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun wrap(bytes: ByteArray): ImmutableByteArray {
            return ImmutableByteArray(bytes)
        }

        fun copyOf(bytes: ByteArray): ImmutableByteArray {
            return ImmutableByteArray(bytes.copyOf())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    operator fun get(index: Int): Byte {
        return bytes[index]
    }


//    fun unwrap(user: (ByteArray) -> Unit) {
//        user.invoke(bytes)
//    }


    fun toByteArray(): ByteArray {
        return bytes.copyOf()
    }


    override fun digest(sink: Digest.Sink) {
        sink.addBytes(bytes)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return bytes.toString()
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ImmutableByteArray

        if (! bytes.contentEquals(other.bytes)) return false

        return true
    }


    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}