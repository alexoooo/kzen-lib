package tech.kzen.lib.common.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


// SER3 spike (test-only, no production code). SER3's own migrated family is three flat leaf DTOs, so it cannot
// speak to the two things SER4 actually finds hard. These throwaway fixtures probe both at near-zero cost, so
// SER3's payoff-gate verdict can be honest about them rather than silent:
//
//   1. A recursive @Serializable in KMP commonMain — LogicRunFrameInfo is a self-referential tree.
//   2. A nullable field WITHOUT a default encoding as an explicit JSON null — SER4 kills LogicStatus's
//      literal "null" string sentinel (LogicStatus.kt) by making `active` exactly this shape.
//
// If either of these ever goes red, SER4's plan needs revisiting BEFORE the migration, not during it.
// Delete this file once SER4 has landed and pins the same behaviour against the real DTOs.
class Ser4SpikeTest {
    //-----------------------------------------------------------------------------------------------------------------
    // Unknown #1: mirrors LogicRunFrameInfo's shape — a node holding a list of itself.
    @Serializable
    private data class FrameSpike(
        val id: String,
        val children: List<FrameSpike> = listOf()
    )


    @Test
    fun recursiveSerializableRoundTripsInCommonMain() {
        val tree = FrameSpike("root", listOf(
            FrameSpike("a", listOf(
                FrameSpike("a1"),
                FrameSpike("a2", listOf(FrameSpike("a2i"))))),
            FrameSpike("b")))

        val encoded = Json.encodeToString(tree)
        assertEquals(tree, Json.decodeFromString<FrameSpike>(encoded))
        assertTrue(encoded.contains("a2i"), "deep node lost: $encoded")
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Unknown #2: mirrors LogicStatus.active — nullable, NO default.
    @Serializable
    private data class StatusSpike(
        val epoch: String,
        val active: ActiveSpike?
    )

    @Serializable
    private data class ActiveSpike(val runId: String)


    @Test
    fun nullableWithoutDefaultEncodesExplicitJsonNull() {
        // This is what lets SER4 delete the "null"-string sentinel: a nullable property with no default encodes
        // as a real JSON null (encodeDefaults=false can't skip it — there is no default to compare against), and
        // decodes back to null. Stock Json, no explicitNulls tweak.
        val expected = buildJsonObject {
            put("epoch", "7")
            put("active", JsonNull)
        }
        val encoded = Json.encodeToJsonElement(StatusSpike("7", null))
        assertEquals(expected, encoded)
        assertEquals(JsonNull, (encoded as JsonObject)["active"])

        assertNull(Json.decodeFromString<StatusSpike>("""{"epoch":"7","active":null}""").active)
        assertEquals(
            ActiveSpike("r1"),
            Json.decodeFromString<StatusSpike>("""{"epoch":"7","active":{"runId":"r1"}}""").active)
    }
}
