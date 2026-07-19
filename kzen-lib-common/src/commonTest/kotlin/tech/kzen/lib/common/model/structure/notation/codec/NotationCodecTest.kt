package tech.kzen.lib.common.model.structure.notation.codec

import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull


class NotationCodecTest {
    //-----------------------------------------------------------------------------------------------------------------
    private enum class Color { Red, Green, Blue }

    private data class Sample(
        val name: String,
        val count: Int,
        val colours: Set<Color>
    )

    private val sampleCodec: NotationCodec<Sample> = NotationCodecs.record(
        decode = {
            Sample(
                it.field("name", NotationCodecs.scalar),
                it.field("count", NotationCodecs.int),
                it.field("colours", NotationCodecs.set(NotationCodecs.enum<Color>())))
        },
        encode = {
            listOf(
                "name" to NotationCodecs.scalar.unparse(it.name),
                "count" to NotationCodecs.int.unparse(it.count),
                "colours" to NotationCodecs.set(NotationCodecs.enum<Color>()).unparse(it.colours))
        })


    private fun <T> assertRoundTrip(codec: NotationCodec<T>, value: T) {
        assertEquals(value, codec.parse(codec.unparse(value)))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun scalarRoundTrip() {
        assertEquals("hello", NotationCodecs.scalar.parse(ScalarAttributeNotation("hello")))
        assertEquals(ScalarAttributeNotation("hello"), NotationCodecs.scalar.unparse("hello"))
        assertRoundTrip(NotationCodecs.scalar, "world")
    }


    @Test
    fun booleanRoundTrip() {
        assertEquals(true, NotationCodecs.boolean.parse(ScalarAttributeNotation("true")))
        assertEquals(ScalarAttributeNotation("false"), NotationCodecs.boolean.unparse(false))
        assertRoundTrip(NotationCodecs.boolean, true)
        assertRoundTrip(NotationCodecs.boolean, false)
    }


    @Test
    fun intAndLongAndDoubleRoundTrip() {
        assertEquals(42, NotationCodecs.int.parse(ScalarAttributeNotation("42")))
        assertRoundTrip(NotationCodecs.int, -7)
        assertRoundTrip(NotationCodecs.long, 9_000_000_000L)
        assertRoundTrip(NotationCodecs.double, 3.5)
    }


    @Test
    fun enumRoundTrip() {
        assertEquals(Color.Green, NotationCodecs.enum<Color>().parse(ScalarAttributeNotation("Green")))
        assertEquals(ScalarAttributeNotation("Blue"), NotationCodecs.enum<Color>().unparse(Color.Blue))
        assertRoundTrip(NotationCodecs.enum<Color>(), Color.Red)
    }


    @Test
    fun enumRejectsUnknown() {
        assertFailsWith<IllegalArgumentException> {
            NotationCodecs.enum<Color>().parse(ScalarAttributeNotation("Purple"))
        }
    }


    @Test
    fun scalarMappedRoundTrip() {
        val codec = NotationCodecs.scalarMapped({ it.length }, { "x".repeat(it) })
        assertEquals(3, codec.parse(ScalarAttributeNotation("abc")))
        assertEquals(ScalarAttributeNotation("xx"), codec.unparse(2))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun listPreservesOrder() {
        val codec = NotationCodecs.list(NotationCodecs.scalar)
        val notation = ListAttributeNotation(persistentListOf(
            ScalarAttributeNotation("a"),
            ScalarAttributeNotation("b"),
            ScalarAttributeNotation("c")))

        assertEquals(listOf("a", "b", "c"), codec.parse(notation))
        assertRoundTrip(codec, listOf("z", "y", "x"))
    }


    @Test
    fun setPreservesInsertionOrder() {
        val codec = NotationCodecs.set(NotationCodecs.scalar)
        val value = linkedSetOf("first", "second", "third")
        val notation = codec.unparse(value) as ListAttributeNotation
        assertEquals(listOf("first", "second", "third"), notation.values.map { it.asString() })
        assertEquals(value, codec.parse(notation))
    }


    @Test
    fun mapPreservesInsertionOrder() {
        val codec = NotationCodecs.map({ it }, { it }, NotationCodecs.int)
        val value = linkedMapOf("one" to 1, "two" to 2, "three" to 3)

        val notation = codec.unparse(value) as MapAttributeNotation
        assertEquals(listOf("one", "two", "three"), notation.map.keys.map { it.asKey() })

        val parsed = codec.parse(notation)
        assertEquals(listOf("one", "two", "three"), parsed.keys.toList())
        assertEquals(value, parsed)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun recordRoundTrip() {
        val sample = Sample("widget", 5, linkedSetOf(Color.Red, Color.Blue))
        assertRoundTrip(sampleCodec, sample)

        val notation = sampleCodec.unparse(sample) as MapAttributeNotation
        assertEquals(listOf("name", "count", "colours"), notation.map.keys.map { it.asKey() })
        assertEquals(ScalarAttributeNotation("widget"), notation["name"])
        assertEquals(ScalarAttributeNotation("5"), notation["count"])
    }


    @Test
    fun fieldDefaultAndOrNull() {
        val notation = recordOf("present" to ScalarAttributeNotation("here"))
        assertEquals("here", notation.field("present", NotationCodecs.scalar, "fallback"))
        assertEquals("fallback", notation.field("absent", NotationCodecs.scalar, "fallback"))
        assertNull(notation.fieldOrNull("absent", NotationCodecs.scalar))
    }


    @Test
    fun fieldRequiredThrowsWhenAbsent() {
        val notation = MapAttributeNotation.empty
        assertFailsWith<IllegalArgumentException> {
            notation.field("missing", NotationCodecs.scalar)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun xmapWrapsValue() {
        data class Wrapped(val inner: List<String>)
        val codec = NotationCodecs.list(NotationCodecs.scalar)
            .xmap({ Wrapped(it) }, { it.inner })
        assertRoundTrip(codec, Wrapped(listOf("p", "q")))
    }
}
