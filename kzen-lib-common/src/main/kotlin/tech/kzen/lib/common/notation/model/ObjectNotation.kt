package tech.kzen.lib.common.notation.model


data class ObjectNotation(
        val parameters: Map<String, ParameterNotation>)
{
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
}
