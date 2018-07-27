package tech.kzen.lib.common.notation.model


data class PackageNotation(
        val objects: Map<String, ObjectNotation>
) {
    fun withModifiedObject(
            objectName: String,
            objectNotation: ObjectNotation
    ): PackageNotation {
        check(objects.containsKey(objectName), { "Not found: $objectName" })

        return withObject(objectName, objectNotation)
    }


    fun withNewObject(
            objectName: String,
            objectNotation: ObjectNotation
    ): PackageNotation {
        check(! objects.containsKey(objectName), { "Already exists: $objectName" })

        return withObject(objectName, objectNotation)
    }


    private fun withObject(
            objectName: String,
            objectNotation: ObjectNotation
    ): PackageNotation {
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


    fun withoutObject(
            objectName: String
    ): PackageNotation {
        check(objects.containsKey(objectName), { "Not found: $objectName" })

        val buffer = mutableMapOf<String, ObjectNotation>()

        for (e in objects) {
            if (e.key == objectName) {
                continue
            }

            buffer[e.key] = e.value
        }

        return PackageNotation(buffer)
    }
}
