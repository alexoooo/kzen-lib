package tech.kzen.lib.common.service.store.normal

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.CopiedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.DeletedDocumentEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RemovedObjectEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedDocumentRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedNestedObjectEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.RenamedObjectRefactorEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpdatedInAttributeEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals


class ObjectStableMapperTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `simple object rename preserves stable id`() {
        val mapper = ObjectStableMapper()
        val before = objectLocation("a.yaml", "B")
        val id = mapper.objectStableId(before)

        mapper.apply(RenamedObjectEvent(before, ObjectName("B2")))

        val after = objectLocation("a.yaml", "B2")
        assertEquals(id, mapper.objectStableId(after))
        assertEquals(after, mapper.objectLocation(id))
    }


    @Test
    fun `simple document rename preserves stable id`() {
        val mapper = ObjectStableMapper()
        val before = objectLocation("a.yaml", "B")
        val id = mapper.objectStableId(before)

        mapper.apply(renamedDocumentRefactorEvent("a.yaml", "c.yaml"))

        val after = objectLocation("c.yaml", "B")
        assertEquals(id, mapper.objectStableId(after))
        assertEquals(after, mapper.objectLocation(id))
    }


    @Test
    fun `document rename round-trip leaves stable id at original location`() {
        // A/B -> C/B -> A/B
        val mapper = ObjectStableMapper()
        val start = objectLocation("a.yaml", "B")
        val id = mapper.objectStableId(start)

        mapper.apply(renamedDocumentRefactorEvent("a.yaml", "c.yaml"))
        assertEquals(objectLocation("c.yaml", "B"), mapper.objectLocation(id))

        mapper.apply(renamedDocumentRefactorEvent("c.yaml", "a.yaml"))
        assertEquals(start, mapper.objectLocation(id))
        assertEquals(id, mapper.objectStableId(start))
    }


    @Test
    fun `chain of mixed document and object renames preserves stable id`() {
        // A/B -> C/B -> C/A -> B/A
        val mapper = ObjectStableMapper()
        val start = objectLocation("a.yaml", "B")
        val id = mapper.objectStableId(start)

        // A/B -> C/B (document rename)
        mapper.apply(renamedDocumentRefactorEvent("a.yaml", "c.yaml"))
        assertEquals(objectLocation("c.yaml", "B"), mapper.objectLocation(id))

        // C/B -> C/A (object rename)
        mapper.apply(RenamedObjectEvent(objectLocation("c.yaml", "B"), ObjectName("A")))
        assertEquals(objectLocation("c.yaml", "A"), mapper.objectLocation(id))

        // C/A -> B/A (document rename)
        mapper.apply(renamedDocumentRefactorEvent("c.yaml", "b.yaml"))
        assertEquals(objectLocation("b.yaml", "A"), mapper.objectLocation(id))

        assertEquals(id, mapper.objectStableId(objectLocation("b.yaml", "A")))
    }


    @Test
    fun `name swap via temporary preserves both stable ids`() {
        // Two coexisting objects A and B in the same document. Swap their names via a tmp rename.
        val mapper = ObjectStableMapper()
        val a = objectLocation("d.yaml", "A")
        val b = objectLocation("d.yaml", "B")
        val idA = mapper.objectStableId(a)
        val idB = mapper.objectStableId(b)
        assertNotEquals(idA, idB)

        // A -> tmp
        mapper.apply(RenamedObjectEvent(a, ObjectName("tmp")))
        // B -> A
        mapper.apply(RenamedObjectEvent(b, ObjectName("A")))
        // tmp -> B
        mapper.apply(RenamedObjectEvent(objectLocation("d.yaml", "tmp"), ObjectName("B")))

        // The id originally pointing at A now points at B (and vice versa)
        assertEquals(objectLocation("d.yaml", "B"), mapper.objectLocation(idA))
        assertEquals(objectLocation("d.yaml", "A"), mapper.objectLocation(idB))
    }


    @Test
    fun `nested object rename preserves stable id`() {
        val mapper = ObjectStableMapper()
        val nested = ObjectLocation(
            DocumentPath.parse("d.yaml"),
            ObjectPath(ObjectName("Leaf"), ObjectNesting.parse("main.steps/Parent.children")))
        val id = mapper.objectStableId(nested)

        val newNesting = ObjectNesting.parse("main.steps/Renamed.children")
        mapper.apply(RenamedNestedObjectEvent(nested, newNesting))

        val afterLocation = ObjectLocation(
            DocumentPath.parse("d.yaml"),
            ObjectPath(ObjectName("Leaf"), newNesting))
        assertEquals(afterLocation, mapper.objectLocation(id))
        assertEquals(id, mapper.objectStableId(afterLocation))
    }


    @Test
    fun `compound RenamedObjectRefactorEvent applies inner rename and ignores reference adjustments`() {
        val mapper = ObjectStableMapper()
        val before = objectLocation("a.yaml", "Old")
        val id = mapper.objectStableId(before)

        val refactor = RenamedObjectRefactorEvent(
            RenamedObjectEvent(before, ObjectName("New")),
            listOf(UpdatedInAttributeEvent(
                objectLocation("a.yaml", "Caller"),
                AttributePath.parse("input"),
                ScalarAttributeNotation("a.yaml/New"))),
            listOf())
        mapper.apply(refactor)

        val after = objectLocation("a.yaml", "New")
        assertEquals(after, mapper.objectLocation(id))
        // The caller's id is not affected by the reference adjustment
        val callerLocation = objectLocation("a.yaml", "Caller")
        val callerId = mapper.objectStableId(callerLocation)
        assertEquals(callerLocation, mapper.objectLocation(callerId))
    }


    @Test
    fun `compound RenamedDocumentRefactorEvent moves every registered location wholesale`() {
        val mapper = ObjectStableMapper()
        val a1 = objectLocation("a.yaml", "X")
        val a2 = objectLocation("a.yaml", "Y")
        val unrelated = objectLocation("other.yaml", "Z")
        val idA1 = mapper.objectStableId(a1)
        val idA2 = mapper.objectStableId(a2)
        val idUnrelated = mapper.objectStableId(unrelated)

        mapper.apply(renamedDocumentRefactorEvent("a.yaml", "moved.yaml"))

        assertEquals(objectLocation("moved.yaml", "X"), mapper.objectLocation(idA1))
        assertEquals(objectLocation("moved.yaml", "Y"), mapper.objectLocation(idA2))
        assertEquals(unrelated, mapper.objectLocation(idUnrelated))
    }


    @Test
    fun `rename of unregistered location is a no-op`() {
        val mapper = ObjectStableMapper()
        val registered = objectLocation("a.yaml", "Reg")
        val idReg = mapper.objectStableId(registered)

        val unregistered = objectLocation("a.yaml", "NotReg")
        mapper.apply(RenamedObjectEvent(unregistered, ObjectName("Renamed")))

        // The registered entry is untouched
        assertEquals(registered, mapper.objectLocation(idReg))
        // A fresh lookup for the renamed-from-unregistered location produces a distinct id
        val freshId = mapper.objectStableId(objectLocation("a.yaml", "Renamed"))
        assertNotEquals(idReg, freshId)
    }


    @Test
    fun `RemovedObjectEvent drops the entry`() {
        val mapper = ObjectStableMapper()
        val location = objectLocation("a.yaml", "Doomed")
        val survivor = objectLocation("a.yaml", "Survivor")
        val id = mapper.objectStableId(location)
        val survivorId = mapper.objectStableId(survivor)
        assertEquals(location, mapper.objectLocation(id))

        mapper.apply(RemovedObjectEvent(location))

        assertFailsWith<IllegalArgumentException> {
            mapper.objectLocation(id)
        }
        // Unrelated entries are untouched
        assertEquals(survivor, mapper.objectLocation(survivorId))
    }


    @Test
    fun `DeletedDocumentEvent drops every entry under that document`() {
        val mapper = ObjectStableMapper()
        val a1 = objectLocation("doomed.yaml", "X")
        val a2 = objectLocation("doomed.yaml", "Y")
        val survivor = objectLocation("other.yaml", "Z")
        val idA1 = mapper.objectStableId(a1)
        val idA2 = mapper.objectStableId(a2)
        val idSurvivor = mapper.objectStableId(survivor)

        mapper.apply(DeletedDocumentEvent(DocumentPath.parse("doomed.yaml")))

        assertFailsWith<IllegalArgumentException> { mapper.objectLocation(idA1) }
        assertFailsWith<IllegalArgumentException> { mapper.objectLocation(idA2) }
        assertEquals(survivor, mapper.objectLocation(idSurvivor))
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun objectLocation(documentPath: String, objectName: String): ObjectLocation {
        return ObjectLocation(
            DocumentPath.parse(documentPath),
            ObjectPath.parse(objectName))
    }


    private fun renamedDocumentRefactorEvent(from: String, to: String): RenamedDocumentRefactorEvent {
        val fromPath = DocumentPath.parse(from)
        val toPath = DocumentPath.parse(to)
        return RenamedDocumentRefactorEvent(
            CopiedDocumentEvent(fromPath, toPath),
            DeletedDocumentEvent(fromPath),
            listOf())
    }
}
