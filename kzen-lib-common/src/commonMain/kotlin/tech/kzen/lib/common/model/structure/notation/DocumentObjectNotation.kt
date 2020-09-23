package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap


data class DocumentObjectNotation(
        val notations: ObjectPathMap<ObjectNotation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = DocumentObjectNotation(ObjectPathMap.empty())
    }


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
    override fun equals(other: Any?): Boolean {
        val otherNotations = (other as? DocumentObjectNotation)?.notations
                ?: return false

        return notations.equalsInOrder(otherNotations)
    }


    override fun hashCode(): Int {
        return notations.hashCode()
    }
}