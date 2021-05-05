package tech.kzen.lib.common.model.structure.notation

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNameMap
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.Digest
import tech.kzen.lib.common.util.Digestible
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.persistentMapOf


data class ObjectNotation(
    val attributes: AttributeNameMap<AttributeNotation>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val className = ClassName(
                "tech.kzen.lib.common.model.structure.notation.ObjectNotation")


        fun ofParent(name: ObjectName): ObjectNotation {
            return ofParent(ObjectReference.ofRootName(name))
        }


        fun ofParent(reference: ObjectReference): ObjectNotation {
            val attributeNotation = ScalarAttributeNotation(reference.asString())

            return ObjectNotation(AttributeNameMap(persistentMapOf(
                    NotationConventions.isAttributePath.attribute to attributeNotation)))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var digest: Digest? = null


    //-----------------------------------------------------------------------------------------------------------------
    fun get(notationName: AttributeName): AttributeNotation? {
        return attributes.values[notationName]
    }


    fun get(notationPath: AttributePath): AttributeNotation? {
//        if (parameters.containsKey(notationPath)) {
//            return parameters[notationPath]!!
//        }
//
//        val segments = notationPath.split(".")

//        println("attributes: $attributes - $notationPath")

        val firstSegment = notationPath.attribute
        if (! attributes.values.containsKey(firstSegment)) {
//            println("first segment missing ($firstSegment): $attributes - $notationPath")
            return null
        }
//        println("first segment: $firstSegment")

//        val root = attributes.values[firstSegment]!!

        val root = get(notationPath.attribute)
                ?: return null

        if (notationPath.nesting.segments.isEmpty()) {
            return root
        }

        var next: StructuredAttributeNotation = root
                as? StructuredAttributeNotation
                ?: return null

        for (segment in notationPath.nesting.segments.dropLast(1)) {
//            println("get - next: $next - $segment")

            val sub = next.get(segment.asString())
                    as? StructuredAttributeNotation
                    ?: return null

            next = sub
        }

        val lastPathSegment = notationPath.nesting.segments.last()

        return next.get(lastPathSegment.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun upsertAttribute(
        attributeName: AttributeName,
        attributeNotation: AttributeNotation
    ): ObjectNotation {
        return ObjectNotation(
            attributes.put(attributeName, attributeNotation))
    }


    fun upsertAttribute(
        attributePath: AttributePath,
        attributeNotation: AttributeNotation
    ): ObjectNotation {
        val rootParameterName = attributePath.attribute

        if (attributePath.nesting.segments.isEmpty()) {
            return upsertAttribute(rootParameterName, attributeNotation)
        }

        val root: StructuredAttributeNotation = attributes
                .values[rootParameterName]
                as? StructuredAttributeNotation
                ?: MapAttributeNotation.empty

        val newRoot = root.upsert(
                attributePath.nesting,
                attributeNotation)

        return upsertAttribute(rootParameterName, newRoot)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun removeAttribute(
        attributeName: AttributeName
    ): ObjectNotation {
        return ObjectNotation(
            attributes.remove(attributeName))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(builder: Digest.Builder) {
        builder.addDigest(digest())
    }


    override fun digest(): Digest {
        if (digest == null) {
            val builder = Digest.Builder()
            builder.addDigestibleOrderedMap(attributes.values)
            digest = builder.digest()
        }
        return digest!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return attributes.toString()
    }
}
