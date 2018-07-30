package tech.kzen.lib.common.notation.model


data class ObjectNotation(
        val parameters: Map<String, ParameterNotation>
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun get(notationPath: String): ParameterNotation? {
        if (parameters.containsKey(notationPath)) {
            return parameters[notationPath]!!
        }

        val segments = notationPath.split(".")

        if (! parameters.containsKey(segments[0])) {
            return null
        }

        val root = parameters[segments[0]]!!
                as? StructuredParameterNotation
                ?: return null

        var next: StructuredParameterNotation = root

        for (i in 1 until segments.size - 1) {
            val sub = next.get(segments[i])
                    as? StructuredParameterNotation
                    ?: return null

            next = sub
        }

        val lastPathSegment = segments.last()

        return next.get(lastPathSegment)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun upsertParameter(
            notationPath: String,
            parameterNotation: ParameterNotation
    ): ObjectNotation {
        val notationPathSegments = notationPath.split(".")
        val rootParameterName: String = notationPathSegments[0]

        if (notationPathSegments.size == 1) {
            return upsertRootParameter(rootParameterName, parameterNotation)
        }

//        if (! parameters.containsKey(rootParameterName)) {
//            throw IllegalArgumentException("Parameter not found: $notationPath")
//        }

        val root: StructuredParameterNotation =
                parameters[rootParameterName]
                as? StructuredParameterNotation
                ?: MapParameterNotation(mapOf())
//                ?: throw IllegalArgumentException(
//                        "Structured parameter expected (${notationPathSegments[0]}): $notationPath")

        val newRoot = upsertSubParameter(
                root,
                notationPathSegments.subList(1, notationPathSegments.size),
                parameterNotation)

        return upsertRootParameter(notationPath, newRoot)
    }


    private fun upsertRootParameter(
            parameterName: String,
            value: ParameterNotation
    ): ObjectNotation {
        var replaced = false

        val buffer = mutableMapOf<String, ParameterNotation>()
        for (parameter in parameters) {
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
            next: StructuredParameterNotation,
            remainingPathSegments: List<String>,
            value: ParameterNotation
    ): StructuredParameterNotation {
        val nextPathSegment = remainingPathSegments[0]

        val nextValue =
                if (remainingPathSegments.size == 1) {
                    value
                }
                else {
                    upsertSubParameter(
                            next.get(nextPathSegment) as StructuredParameterNotation,
                            remainingPathSegments.subList(1, remainingPathSegments.size),
                            value)
                }

        return when (next) {
            is ListParameterNotation -> {
                val index =  nextPathSegment.toInt()

                val buffer = mutableListOf<ParameterNotation>()
                buffer.addAll(next.values)

                buffer[index] = nextValue

                ListParameterNotation(buffer)
            }

            is MapParameterNotation -> {
                val buffer = mutableMapOf<String, ParameterNotation>()

                for (e in next.values) {
                    buffer[e.key] =
                            if (e.key == nextPathSegment) {
                                nextValue
                            }
                            else {
                                e.value
                            }
                }

                MapParameterNotation(buffer)
            }
        }
    }
}
