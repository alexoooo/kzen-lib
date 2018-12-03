package tech.kzen.lib.common.util

import tech.kzen.lib.platform.IoUtils
import kotlin.test.Test
import kotlin.test.assertEquals


class IoUtilsTest {
    @Test
    fun emptyString() {
        encodeAndDecode("")
    }


    @Test
    fun simpleString() {
        encodeAndDecode("foo")
    }


    @Test
    fun asciiString() {
        encodeAndDecode("!@#\$zxcv?|\"]'")
    }


    @Test
    fun unicodeString() {
        encodeAndDecode("~§·")
    }


    private fun encodeAndDecode(value: String) {
        assertEquals(
                value,
                IoUtils.utf8ToString(IoUtils.stringToUtf8(value)))
    }
}
