package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible


data class DocumentObjectNotation(
    val notations: ObjectPathMap<ObjectNotation>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = DocumentObjectNotation(ObjectPathMap.empty())
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var digest: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun withModifiedObject(
            objectPath: ObjectPath,
            objectNotation: ObjectNotation
    ): DocumentObjectNotation {
        return DocumentObjectNotation(
                notations.updateEntry(objectPath, objectNotation))
    }


    fun withNewObject(
            positionedObjectPath: PositionedObjectPath,
            objectNotation: ObjectNotation
    ): DocumentObjectNotation {
        return DocumentObjectNotation(
                notations.insertEntry(positionedObjectPath, objectNotation))
    }


    fun withoutObject(
            objectPath: ObjectPath
    ): DocumentObjectNotation {
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

            builder.addDigestibleOrderedMap(notations.values)

            digest = builder.digest()
        }
        return digest!!
    }


    override fun equals(other: Any?): Boolean {
        val otherNotations = (other as? DocumentObjectNotation)?.notations
                ?: return false

        return notations.equalsInOrder(otherNotations)
    }


    override fun hashCode(): Int {
        return notations.hashCode()
    }
}