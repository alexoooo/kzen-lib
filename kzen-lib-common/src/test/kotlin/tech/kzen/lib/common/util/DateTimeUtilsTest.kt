package tech.kzen.lib.common.util

import tech.kzen.lib.platform.DateTimeUtils
import kotlin.test.Test
import kotlin.test.assertTrue


class DateTimeUtilsTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyString() {
        // 20190728_162234_868
        assertTrue(DateTimeUtils
                .filenameTimestamp()
                .matches(Regex("""\d\d\d\d\d\d\d\d_\d\d\d\d\d\d_\d\d\d""")),
                "Pattern match failed")
    }
}
