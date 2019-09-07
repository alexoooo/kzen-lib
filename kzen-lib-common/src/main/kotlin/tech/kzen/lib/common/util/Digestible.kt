package tech.kzen.lib.common.util


interface Digestible {
    fun digest(digester: Digest.Builder)

    fun digest(): Digest {
        val digester = Digest.Builder()
        digest(digester)
        return digester.digest()
    }
}