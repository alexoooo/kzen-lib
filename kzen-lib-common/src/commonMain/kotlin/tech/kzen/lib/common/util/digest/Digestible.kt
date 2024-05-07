package tech.kzen.lib.common.util.digest


interface Digestible {
    fun digest(sink: Digest.Sink)

    // TODO: clarify the relation between this and the above method,
    //    e.g. can this digest() be cached, and then be fed into the sink of the sink above,
    //      or does is the below implementation required to be observed (which would require caching 2 separate values)?
    fun digest(): Digest {
        val builder = Digest.Builder()
        digest(builder)
        return builder.digest()
    }
}