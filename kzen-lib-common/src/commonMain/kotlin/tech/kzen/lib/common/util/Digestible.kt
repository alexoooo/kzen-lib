package tech.kzen.lib.common.util


interface Digestible {
    fun digest(builder: Digest.Builder)

    fun digest(): Digest {
        val builder = Digest.Builder()
        digest(builder)
        return builder.digest()
    }
}