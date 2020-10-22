package tech.kzen.lib.common.util


interface Digestible {
//    companion object {
//        fun of(utf8: String): Digestible {
//            return Digest.ofUtf8()
//        }
//    }


    fun digest(builder: Digest.Builder)

    fun digest(): Digest {
        val builder = Digest.Builder()
        digest(builder)
        return builder.digest()
    }
}