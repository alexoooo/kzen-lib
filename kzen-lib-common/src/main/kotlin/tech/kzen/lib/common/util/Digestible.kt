package tech.kzen.lib.common.util


interface Digestible {
    fun digest(digester: Digest.Streaming)

    fun digest(): Digest {
        val digester = Digest.Streaming()
        digest(digester)
        return digester.digest()
    }
}