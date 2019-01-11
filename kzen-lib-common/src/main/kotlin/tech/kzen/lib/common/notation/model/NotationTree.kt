package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.objects.bootstrap.BootstrapConventions
import tech.kzen.lib.common.api.model.*


// TODO: factor out Map<BundlePath, T> as BundleTree?
data class NotationTree(
        val files: BundleTree<BundleNotation>)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = NotationTree(BundleTree(mapOf()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    val objectPaths: Set<ObjectLocation> by lazy {
        coalesce.values.keys
    }

    val coalesce: ObjectMap<ObjectNotation> by lazy {
        val buffer = mutableMapOf<ObjectLocation, ObjectNotation>()
        files.values.entries
                .flatMap { it.value.expand(it.key).values.entries }
                .forEach { buffer[it.key] = it.value }
        ObjectMap(buffer)
    }

//    val digest: Digest by lazy {
//
//    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: locate ObjectReference relative to some ObjectFilePath/ObjectFileNesting?
//    fun locate(objectName: ObjectName): ObjectLocation {
//        val candidates = mutableListOf<ObjectLocation>()
//        for (candidate in coalesce.values.keys) {
//            if (candidate != objectName) {
//                continue
//            }
//
//            candidates.add(candidate)
//        }
//
//        check(! candidates.isEmpty()) { "Missing: $objectName" }
//        check(candidates.size == 1) { "Ambiguous: $objectName - $candidates" }
//
//        return candidates[0]
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun directParameter(
            objectLocation: ObjectLocation,
            notationPath: AttributeNesting
    ): AttributeNotation? =
            coalesce.values[objectLocation]?.get(notationPath)


    fun transitiveParameter(
            objectLocation: ObjectLocation,
            notationPath: AttributeNesting
    ): AttributeNotation? {
        val notation = coalesce.values[objectLocation] ?: return null

        val parameter = notation.get(notationPath)
        if (parameter != null) {
//            println("== parameter - $objectName - $notationPath: $parameter")
            return parameter
        }

        val isParameter = notation.get(NotationConventions.isPath)

        val superReference =
                when (isParameter) {
                    null -> ObjectReference(ObjectName("Object"), null, null)
                    !is ScalarAttributeNotation -> TODO()
                    else -> {
                        val isValue = isParameter.value

                        @Suppress("FoldInitializerAndIfToElvis")
                        if (isValue !is String) {
                            TODO()
                        }

                        ObjectReference.parse(isValue)
                    }
                }

        val superLocation = coalesce.locate(objectLocation, superReference)

        if (superLocation == BootstrapConventions.rootObjectLocation ||
                superLocation == BootstrapConventions.bootstrapLocation) {
            return null
        }

//        println("^^^^^ superName: $superName")

        return transitiveParameter(superLocation, notationPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun getString(objectName: ObjectName, notationPath: ObjectNotationPath): String {
    fun getString(objectLocation: ObjectLocation, notationPath: AttributeNesting): String {
        val scalarParameter = transitiveParameter(objectLocation, notationPath)
                ?: throw IllegalArgumentException("Not found: $objectLocation.$notationPath")

        return scalarParameter.asString()
            ?: throw IllegalArgumentException("Expected string ($objectLocation.$notationPath): $scalarParameter")
    }


    //-----------------------------------------------------------------------------------------------------------------
//    fun findPackage(objectPath: ObjectPath): BundlePath {
//        for (e in files) {
//            if (e.value.objects.values.containsKey(objectPath)) {
//                return e.key
//            }
//        }
//        throw IllegalArgumentException("Unknown object: $objectPath")
//    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withNewPackage(
            projectPath: BundlePath,
            packageNotation: BundleNotation
    ): NotationTree {
        check(! files.values.containsKey(projectPath)) {"Already exists: $projectPath"}

        val buffer = mutableMapOf<BundlePath, BundleNotation>()

        buffer.putAll(files.values)

        buffer[projectPath] = packageNotation

        return NotationTree(BundleTree(buffer))
    }


    fun withModifiedPackage(
            projectPath: BundlePath,
            packageNotation: BundleNotation
    ): NotationTree {
        check(files.values.containsKey(projectPath)) {"Not found: $projectPath"}

        val buffer = mutableMapOf<BundlePath, BundleNotation>()

        for (e in files.values) {
            buffer[e.key] =
                    if (e.key == projectPath) {
                        packageNotation
                    }
                    else {
                        e.value
                    }
        }

        return NotationTree(BundleTree(buffer))
    }


    fun withoutPackage(
            projectPath: BundlePath
    ): NotationTree {
        check(files.values.containsKey(projectPath)) {"Already absent: $projectPath"}

        val buffer = mutableMapOf<BundlePath, BundleNotation>()

        for (e in files.values) {
            if (e.key == projectPath) {
                continue
            }

            buffer[e.key] = e.value
        }

        return NotationTree(BundleTree(buffer))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filterPaths(predicate: (BundlePath) -> Boolean): NotationTree {
        val filteredPackages = mutableMapOf<BundlePath, BundleNotation>()

        for (e in files.values) {
            if (! predicate.invoke(e.key)) {
                continue
            }

            filteredPackages[e.key] = e.value
        }

        return NotationTree(BundleTree(filteredPackages))
    }
}