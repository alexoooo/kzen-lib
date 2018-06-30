package tech.kzen.lib.common.notation.model


data class PackageNotation(
        val objects: Map<String, ObjectNotation>
) {
    fun withObject(
            objectName: String,
            objectNotation: ObjectNotation
    ): PackageNotation {
        check(objects.containsKey(objectName), { "Not found: $objectName" })

        val buffer = mutableMapOf<String, ObjectNotation>()

        for (e in objects) {
            buffer[e.key] =
                    if (e.key == objectName) {
                        objectNotation
                    }
                    else {
                        e.value
                    }
        }

        return PackageNotation(buffer)
    }
}
