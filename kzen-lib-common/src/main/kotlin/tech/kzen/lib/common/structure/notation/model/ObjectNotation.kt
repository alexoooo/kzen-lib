package tech.kzen.lib.common.structure.notation.model

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.NotationConventions


data class ObjectNotation(
        val attributes: Map<AttributeName, AttributeNotation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofParent(name: ObjectName): ObjectNotation {
            return ofParent(ObjectReference.ofName(name))
        }


        fun ofParent(reference: ObjectReference): ObjectNotation {
            val attributeNotation = ScalarAttributeNotation(reference.asString())

            return ObjectNotation(mapOf(
                    NotationConventions.isAttribute.attribute to attributeNotation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(notationPath: AttributePath): AttributeNotation? {
//        if (parameters.containsKey(notationPath)) {
//            return parameters[notationPath]!!
//        }
//
//        val segments = notationPath.split(".")

//        println("attributes: $attributes - $notationPath")

        val firstSegment = notationPath.attribute
        if (! attributes.containsKey(firstSegment)) {
//            println("first segment missing ($firstSegment): $attributes - $notationPath")
            return null
        }
//        println("first segment: $firstSegment")

        val root = attributes[firstSegment]!!
        if (notationPath.nesting.segments.isEmpty()) {
            return root
        }

        var next: StructuredAttributeNotation = root
                as? StructuredAttributeNotation
                ?: return null

        for (segment in notationPath.nesting.segments.dropLast(1)) {
            println("get - next: $next - $segment")

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
            attributePath: AttributePath,
            attributeNotation: AttributeNotation
    ): ObjectNotation {
        val rootParameterName = attributePath.attribute

        if (attributePath.nesting.segments.isEmpty()) {
            return upsertRootParameter(rootParameterName, attributeNotation)
        }

//        if (! parameters.containsKey(rootParameterName)) {
//            throw IllegalArgumentException("Parameter not found: $notationPath")
//        }

        val root: StructuredAttributeNotation =
                attributes[rootParameterName]
                as? StructuredAttributeNotation
                ?: MapAttributeNotation(mapOf())
//                ?: throw IllegalArgumentException(
//                        "Structured parameter expected (${notationPathSegments[0]}): $notationPath")

        val newRoot = upsertSubParameter(
                root,
                attributePath.nesting,
                attributeNotation)

        return upsertRootParameter(rootParameterName, newRoot)
    }


    private fun upsertRootParameter(
            parameterName: AttributeName,
            value: AttributeNotation
    ): ObjectNotation {
        var replaced = false

        val buffer = mutableMapOf<AttributeName, AttributeNotation>()
        for (parameter in attributes) {
            buffer[parameter.key] =
                    if (parameter.key == parameterName) {
                        replaced = true
                        value
                    }
                    else {
                        parameter.value
                    }
        }

        if (! replaced) {
            buffer[parameterName] = value
        }

        return ObjectNotation(buffer)
    }


    private fun upsertSubParameter(
            next: StructuredAttributeNotation,
            remainingPathSegments: AttributeNesting,
            value: AttributeNotation
    ): StructuredAttributeNotation {
        val nextPathSegment = remainingPathSegments.segments[0]

        val nextValue =
                if (remainingPathSegments.segments.size == 1) {
                    value
                }
                else {
                    upsertSubParameter(
                            next.get(nextPathSegment.asString()) as StructuredAttributeNotation,
                            remainingPathSegments.shift(),
                            value)
                }

        return when (next) {
            is ListAttributeNotation -> {
                val index = nextPathSegment.asIndex()!!

                val buffer = mutableListOf<AttributeNotation>()
                buffer.addAll(next.values)

                buffer[index] = nextValue

                ListAttributeNotation(buffer)
            }

            is MapAttributeNotation -> {
                val buffer = mutableMapOf<AttributeSegment, AttributeNotation>()

                for (e in next.values) {
                    buffer[e.key] =
                            if (e.key == nextPathSegment) {
                                nextValue
                            }
                            else {
                                e.value
                            }
                }

                MapAttributeNotation(buffer)
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun toString(): String {
        return attributes.toString()
    }
}
