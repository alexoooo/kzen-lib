package tech.kzen.lib.common.exec

import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * [RequestParams.parse] must be the total inverse of [RequestParams.asString]. The empty case is the one that
 * matters in production — a parameterless task submit sends `RequestParams.empty`, whose `asString()` is `""` —
 * and it used to read a key off the single blank segment that splitting `""` produces. That ran past the end of
 * the string: an exception on JVM, and on JS a silent bogus `"" -> [""]` entry, because JS `substring` clamps
 * a negative index instead of failing. Both platforms are asserted (commonTest runs on JVM and a browser).
 */
class RequestParamsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun assertRoundTrip(params: RequestParams) {
        assertEquals(params, RequestParams.parse(params.asString()), "round-trip failed for <$params>")
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun emptyRoundTrips() {
        assertEquals("", RequestParams.empty.asString())
        assertEquals(RequestParams.empty, RequestParams.parse(""))
        assertRoundTrip(RequestParams.empty)
    }


    @Test
    fun singleEntryRoundTrips() {
        assertRoundTrip(RequestParams.of("a" to "1"))
    }


    @Test
    fun repeatedKeyRoundTrips() {
        assertRoundTrip(RequestParams(mapOf("k" to listOf("v1", "v2"))))
    }


    @Test
    fun multipleKeysRoundTrip() {
        assertRoundTrip(RequestParams(mapOf("a" to listOf("1"), "b" to listOf("2", "3"))))
    }


    @Test
    fun emptyValueRoundTrips() {
        assertRoundTrip(RequestParams.of("a" to ""))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun parseIgnoresBlankSegments() {
        // Trailing / doubled separators, which asString() never writes but a hand-written line can carry
        assertEquals(RequestParams.of("a" to "1"), RequestParams.parse("a=1&"))
        assertEquals(RequestParams.of("a" to "1"), RequestParams.parse("&a=1"))
        assertEquals(RequestParams.of("a" to "1"), RequestParams.parse("a=1&&"))
        assertEquals(RequestParams.empty, RequestParams.parse("&"))
    }


    @Test
    fun parseTreatsBareKeyAsEmptyValue() {
        assertEquals(RequestParams.of("flag" to ""), RequestParams.parse("flag"))
        assertEquals(
            RequestParams(mapOf("flag" to listOf(""), "a" to listOf("1"))),
            RequestParams.parse("flag&a=1"))
    }


    @Test
    fun parseKeepsValuesContainingEquals() {
        // Only the FIRST '=' separates; the rest belongs to the value
        assertEquals(RequestParams.of("a" to "1=2"), RequestParams.parse("a=1=2"))
    }
}
