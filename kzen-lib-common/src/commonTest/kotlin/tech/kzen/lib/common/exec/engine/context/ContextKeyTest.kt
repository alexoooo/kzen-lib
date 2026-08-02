package tech.kzen.lib.common.exec.engine.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * The wire contract of the typed address: [ContextKey.asString] renders exactly the plain string the raw /
 * plugin resource API registers under, and [ContextKey.parse] rejects anything it could not have produced.
 * Both matter because the typed and raw surfaces address ONE registry — a rendering that drifted, or a parse
 * that accepted an ambiguous form, would silently split it.
 */
class ContextKeyTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aBareFamilyRendersAsItself() {
        assertEquals("sut", ContextKey.of("sut").asString())
    }


    @Test
    fun aQualifiedKeyRendersWithTheDelimiter() {
        assertEquals("sut:main", ContextKey.of("sut", "main").asString())
    }


    @Test
    fun parseIsTheInverseOfAsString() {
        for (key in listOf(ContextKey.of("sut"), ContextKey.of("sut", "main"), ContextKey.of("db", "reporting"))) {
            assertEquals(key, ContextKey.parse(key.asString()), "round trip of ${key.asString()}")
        }
    }


    @Test
    fun parseSplitsAtTheFirstDelimiter() {
        val parsed = ContextKey.parse("sut:main")
        assertEquals(ContextFamily("sut"), parsed.family)
        assertEquals("main", parsed.qualifier)
    }


    @Test
    fun anUnqualifiedKeyHasNoQualifier() {
        assertEquals(null, ContextKey.parse("sut").qualifier)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun anEmptyFamilyIsRejected() {
        assertFailsWith<IllegalArgumentException> { ContextFamily("") }
        assertFailsWith<IllegalArgumentException> { ContextKey.parse("") }
        assertFailsWith<IllegalArgumentException> { ContextKey.parse(":main") }
    }


    @Test
    fun aFamilyHoldingTheDelimiterIsRejected() {
        // The whole point of the type: what looks like a family but is really a key can no longer be passed
        // where a family is meant.
        assertFailsWith<IllegalArgumentException> { ContextFamily("sut:main") }
    }


    @Test
    fun anEmptyQualifierIsRejected() {
        // "sut:" and "sut" would otherwise be two spellings of one address.
        assertFailsWith<IllegalArgumentException> { ContextKey.parse("sut:") }
        assertFailsWith<IllegalArgumentException> { ContextKey.of("sut", "") }
    }


    @Test
    fun aSecondDelimiterIsRejectedRatherThanReinterpreted() {
        // There is no second qualifier level, so accepting "sut:a:b" would mean choosing one of two readings
        // silently. Loud rejection keeps asString/parse total inverses of each other.
        assertFailsWith<IllegalArgumentException> { ContextKey.parse("sut:a:b") }
        assertFailsWith<IllegalArgumentException> { ContextKey.of("sut", "a:b") }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun anExactSelectorCoversOnlyItsOwnMember() {
        val selector = ExportSelector.Exact(ContextKey.of("db", "primary"))

        assertTrue(selector.covers(ContextKey.of("db", "primary")))
        assertFalse(selector.covers(ContextKey.of("db", "reporting")),
            "an exact export must not carry a sibling qualifier — that is an ownership leak, not a wider gate")
        assertFalse(selector.covers(ContextKey.of("db")))
    }


    @Test
    fun aFamilySelectorCoversTheBareKeyAndEveryQualifier() {
        val selector = ExportSelector.Family(ContextFamily("db"))

        assertTrue(selector.covers(ContextKey.of("db")))
        assertTrue(selector.covers(ContextKey.of("db", "primary")))
        assertTrue(selector.covers(ContextKey.of("db", "reporting")))
        assertFalse(selector.covers(ContextKey.of("dbx")))
        assertFalse(selector.covers(ContextKey.of("other", "db")))
    }


    @Test
    fun parsingAnExportStringPreservesWhatTheStringSetMeant() {
        // A bare declaration always carried the whole family; a qualified one only matched itself.
        assertEquals(ExportSelector.Family(ContextFamily("sut")), ExportSelector.parse("sut"))
        assertEquals(ExportSelector.Exact(ContextKey.of("sut", "main")), ExportSelector.parse("sut:main"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun aPresentNullBindingIsNotMissing() {
        assertEquals(null, BindingLookup.Present(null).valueOrNull())
        assertEquals(null, BindingLookup.Missing.valueOrNull())
        assertTrue(BindingLookup.Present(null) != BindingLookup.Missing,
            "collapsing these is exactly what the lossy string read did")
    }
}
