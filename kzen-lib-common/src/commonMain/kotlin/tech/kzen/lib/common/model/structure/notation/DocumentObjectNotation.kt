package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


data class DocumentObjectNotation(
    val notations: ObjectPathMap<ObjectNotation>,
    // Explicit discriminator: a folder is a pure directory, NOT a document. It carries no objects, but must remain
    // distinguishable from an empty document (which also has no objects) — hence this flag participates in
    // equals/hashCode/digest. See DocumentNotation.folder / DocumentPath(form = Folder).
    val folder: Boolean = false
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = DocumentObjectNotation(ObjectPathMap.empty())

        val folder = DocumentObjectNotation(ObjectPathMap.empty(), folder = true)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isFolder(): Boolean {
        return folder
    }


    // A folder is a pure directory with no objects — it must never be mutated as if it held object notations.
    private fun assertNotFolder() {
        check(!folder) { "Cannot modify objects of a folder (pure directory)" }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var digest: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun withModifiedObject(
        objectPath: ObjectPath,
        objectNotation: ObjectNotation
    ): DocumentObjectNotation {
        assertNotFolder()
        return DocumentObjectNotation(
            notations.updateEntry(objectPath, objectNotation))
    }


    fun withNewObject(
        positionedObjectPath: PositionedObjectPath,
        objectNotation: ObjectNotation
    ): DocumentObjectNotation {
        assertNotFolder()
        return DocumentObjectNotation(
            notations.insertEntry(positionedObjectPath, objectNotation))
    }


    fun withoutObject(
        objectPath: ObjectPath
    ): DocumentObjectNotation {
        assertNotFolder()
        return DocumentObjectNotation(
            notations.removeKey(objectPath))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()

            builder.addBoolean(folder)
            builder.addDigestibleOrderedMap(notations.map)

            digest = builder.digest()
        }
        return digest!!
    }


    override fun equals(other: Any?): Boolean {
        val that = other as? DocumentObjectNotation
            ?: return false

        return folder == that.folder &&
                notations.equalsInOrder(that.notations)
    }


    override fun hashCode(): Int {
        return 31 * notations.hashCode() + folder.hashCode()
    }
}