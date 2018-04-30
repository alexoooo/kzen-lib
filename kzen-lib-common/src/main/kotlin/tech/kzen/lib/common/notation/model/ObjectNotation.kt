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
                as? MapParameterNotation
                ?: return null

        var next: MapParameterNotation = root

        for (i in 1 until segments.size - 1) {
            val sub = next.values[segments[i]]
                    as? MapParameterNotation
                    ?: return null

            next = sub
        }

        val lastPathSegment = segments.last()

        return next.values[lastPathSegment]
    }
}
