package tech.kzen.lib.platform

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")
actual data class ClassName actual constructor(
    private val asString: String
):
    Digestible
{
    init {
        require(! asString.endsWith("?"))
    }


    actual fun get(): String {
        return asString
    }


    actual override fun digest(sink: Digest.Sink) {
        sink.addUtf8(asString)
    }


    override fun toString(): String {
        return asString
    }


    actual fun asString(): String {
        return asString
    }
}