package tech.kzen.lib.platform

import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")
actual data class ClassName actual constructor(
        private val asString: String
):
    Digestible
{
//    actual companion object {
//        actual fun ofString(asString: String): ClassName {
//            return ClassName(asString)
//        }
//    }

    actual fun get(): String {
        return asString
    }


    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(asString)
    }


    override fun toString(): String {
        return asString
    }


    actual fun asString(): String {
        return asString
    }
}