package tech.kzen.lib.common.util


interface Digestible {
    fun digest(sink: Digest.Sink)

    fun digest(): Digest {
        val builder = Digest.Builder()
        digest(builder)
        return builder.digest()
    }
}