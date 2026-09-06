package tech.kzen.lib.common.exec.data.value

import tech.kzen.lib.common.exec.data.problem.DataProblem
import tech.kzen.lib.common.exec.data.type.FieldId
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


/** The host-supplied liveness guard: a native the host marked closed fails by name on the next read. */
class NativeLivenessGuardTest {
    data class Holder(val label: String, val nested: Holder?)


    @Test
    fun closedNativeFailsByNameOnTheNextReadAndOtherValuesAreUntouched() {
        val closed = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val guard = NativeLivenessGuard { native ->
            if (native in closed) {
                throw DataAccessException(DataProblem(
                    DataProblem.invalidState, "Native ${native.javaClass.simpleName} was closed by its owner"))
            }
        }
        DefaultDataAdapterRegistry(livenessGuard = guard).use { registry ->
            val holder = Holder("outer", Holder("inner", null))
            val value = registry.lift(holder)
            assertEquals("outer", value.access.readText(value.access.field(value.root, FieldId("label"))))

            closed += holder
            val error = assertFailsWith<DataAccessException> { value.access.field(value.root, FieldId("nested")) }
            assertTrue(error.problem.message.contains("closed by its owner"), error.problem.message)
            assertEquals(DataProblem.invalidState, error.problem.code)

            // An unrelated value, and a scalar, read as before
            val other = registry.lift(Holder("other", null))
            assertEquals("other", other.access.readText(other.access.field(other.root, FieldId("label"))))
            val scalar = registry.lift("text")
            assertEquals("text", scalar.access.readText(scalar.root))
        }
    }
}
