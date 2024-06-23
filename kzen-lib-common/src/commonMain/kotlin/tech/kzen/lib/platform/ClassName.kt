package tech.kzen.lib.platform

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ClassName(asString: String) :
    Digestible
{
    fun get(): String

    fun asString(): String


    override fun digest(sink: Digest.Sink)
}