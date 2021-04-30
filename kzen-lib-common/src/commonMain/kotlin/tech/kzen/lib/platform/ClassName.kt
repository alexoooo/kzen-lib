package tech.kzen.lib.platform

import tech.kzen.lib.common.util.Digestible


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