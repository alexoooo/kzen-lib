package tech.kzen.lib.common.notation.model


data class ProjectNotation(
        val packages: Map<ProjectPath, PackageNotation>)
{
    //-----------------------------------------------------------------------------------------------------------------
    val objectNames: Set<String> by lazy {
        coalesce.keys
    }

    val coalesce: Map<String, ObjectNotation> by lazy {
        val buffer = mutableMapOf<String, ObjectNotation>()
        packages.values
                .flatMap { it.objects.entries }
                .forEach { buffer.put(it.key, it.value) }
        buffer
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun directParameter(objectName: String, notationPath: String): ParameterNotation? =
            coalesce[objectName]?.get(notationPath)


    fun transitiveParameter(objectName: String, notationPath: String): ParameterNotation? {
        val notation = coalesce[objectName] ?: return null

        val parameter = notation.get(notationPath)
        if (parameter != null) {
//            println("== parameter - $objectName - $notationPath: $parameter")
            return parameter
        }

        val isParameter = notation.get("is")

        val superName: String =
                when (isParameter) {
                    null -> "Object"
                    !is ScalarParameterNotation -> TODO()
                    else -> {
                        val isValue = isParameter.value

                        @Suppress("FoldInitializerAndIfToElvis")
                        if (isValue !is String) {
                            TODO()
                        }

                        isValue
                    }
                }

        if (objectName == superName) {
            return null
        }

//        println("^^^^^ superName: $superName")
        return transitiveParameter(superName, notationPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun getString(objectName: String, notationPath: String): String {
        val scalarParameter = transitiveParameter(objectName, notationPath)
                ?: throw IllegalArgumentException("Not found: $objectName.$notationPath")

        if (scalarParameter !is ScalarParameterNotation) {
            throw IllegalArgumentException("Expected scalar ($objectName.$notationPath): $scalarParameter")
        }

        val stringValue = scalarParameter.value
        if (stringValue !is String) {
            throw IllegalArgumentException("Expected String ($objectName.$notationPath): $stringValue")
        }

        return stringValue
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun findPackage(objectName: String): ProjectPath {
        for (e in packages) {
            if (e.value.objects.containsKey(objectName)) {
                return e.key
            }
        }
        throw IllegalArgumentException("Unknown object: $objectName")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withPackage(
            projectPath: ProjectPath,
            packageNotation: PackageNotation
    ): ProjectNotation {
        check(packages.containsKey(projectPath), {"Not found: $projectPath"})

        val buffer = mutableMapOf<ProjectPath, PackageNotation>()

        for (e in packages) {
            buffer[e.key] =
                    if (e.key == projectPath) {
                        packageNotation
                    }
                    else {
                        e.value
                    }
        }

        return ProjectNotation(buffer)
    }
}