package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.api.model.*


data class ObjectNotation(
        val attributes: Map<AttributeName, AttributeNotation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun get(notationPath: AttributeNesting): AttributeNotation? {
//        if (parameters.containsKey(notationPath)) {
//            return parameters[notationPath]!!
//        }
//
//        val segments = notationPath.split(".")

        val firstSegment = notationPath.attribute
        if (! attributes.containsKey(firstSegment)) {
            return null
        }

        val root = attributes[firstSegment]!!
                as? StructuredAttributeNotation
                ?: return null

        var next: StructuredAttributeNotation = root

        for (i in 0 .. notationPath.segments.size) {
            val sub = next.get(notationPath.segments[i].asString())
                    as? StructuredAttributeNotation
                    ?: return null

            next = sub
        }

        val lastPathSegment = notationPath.segments.last()

        return next.get(lastPathSegment.asString())
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun upsertParameter(
            notationPath: AttributeNesting,
            parameterNotation: AttributeNotation
    ): ObjectNotation {
        val rootParameterName = notationPath.attribute

        if (notationPath.segments.isEmpty()) {
            return upsertRootParameter(rootParameterName, parameterNotation)
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
                notationPath.segments.subList(1, notationPath.segments.size),
                parameterNotation)

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
            remainingPathSegments: List<AttributeSegment>,
            value: AttributeNotation
    ): StructuredAttributeNotation {
        val nextPathSegment = remainingPathSegments[0]

        val nextValue =
                if (remainingPathSegments.size == 1) {
                    value
                }
                else {
                    upsertSubParameter(
                            next.get(nextPathSegment.asString()) as StructuredAttributeNotation,
                            remainingPathSegments.subList(1, remainingPathSegments.size),
                            value)
                }

        return when (next) {
            is ListAttributeNotation -> {
                val index = (nextPathSegment as ListIndexAttributeSegment).index

                val buffer = mutableListOf<AttributeNotation>()
                buffer.addAll(next.values)

                buffer[index] = nextValue

                ListAttributeNotation(buffer)
            }

            is MapAttributeNotation -> {
                val buffer = mutableMapOf<MapKeyAttributeSegment, AttributeNotation>()

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
//    fun digest(): Digest {
//        val parameters: Map<String, ParameterNotation>
//    }
}
