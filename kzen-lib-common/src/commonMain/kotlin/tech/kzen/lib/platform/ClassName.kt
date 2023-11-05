package tech.kzen.lib.platform

import tech.kzen.lib.common.util.Digestible


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ClassName(asString: String) :
    Digestible
{
//    companion object {
//        fun ofString(asString: String): ClassName
//    }

//    val jvmClassName: String

    fun get(): String


    fun asString(): String
}