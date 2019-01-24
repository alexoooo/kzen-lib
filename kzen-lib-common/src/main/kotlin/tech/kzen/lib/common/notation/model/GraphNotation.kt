package tech.kzen.lib.common.notation.model

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.notation.NotationConventions
import tech.kzen.lib.common.objects.bootstrap.BootstrapConventions


data class GraphNotation(
        val bundleNotations: BundleTree<BundleNotation>)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = GraphNotation(BundleTree(mapOf()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    val objectLocations: Set<ObjectLocation> by lazy {
        coalesce.values.keys
    }

    val coalesce: ObjectMap<ObjectNotation> by lazy {
        val buffer = mutableMapOf<ObjectLocation, ObjectNotation>()
        bundleNotations.values.entries
                .flatMap { it.value.expand(it.key).values.entries }
                .forEach { buffer[it.key] = it.value }
        ObjectMap(buffer)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun directParameter(
            objectLocation: ObjectLocation,
            attributeNesting: AttributePath
    ): AttributeNotation? =
            coalesce.values[objectLocation]?.get(attributeNesting)


    fun transitiveParameter(
            objectLocation: ObjectLocation,
            attributeNesting: AttributePath
    ): AttributeNotation? {
        val notation = coalesce.values[objectLocation]
                ?: return null

        val parameter = notation.get(attributeNesting)
        if (parameter != null) {
//            println("== parameter - $objectName - $notationPath: $parameter")
            return parameter
        }

        val isParameter = notation.get(NotationConventions.isAttribute)

        val superReference =
                when (isParameter) {
                    null ->
                        BootstrapConventions.rootObjectReference

                    !is ScalarAttributeNotation ->
                        TODO()

                    else -> {
                        val isValue = isParameter.value

                        @Suppress("FoldInitializerAndIfToElvis")
                        if (isValue !is String) {
                            TODO()
                        }

                        ObjectReference.parse(isValue)
                    }
                }

        println("coalesce keys ($objectLocation - $attributeNesting - $superReference): " + coalesce.values.keys)
        val superLocation = coalesce.locate(objectLocation, superReference)

        if (objectLocation == BootstrapConventions.rootObjectLocation ||
                objectLocation == BootstrapConventions.bootstrapLocation) {
            return null
        }

//        println("^^^^^ superName: $superName")

        return transitiveParameter(superLocation, attributeNesting)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun getString(attributeLocation: AttributeLocation): String {
        return getString(attributeLocation.objectLocation, attributeLocation.attributePath)
    }

    fun getString(objectLocation: ObjectLocation, attributeNesting: AttributePath): String {
        val scalarParameter = transitiveParameter(objectLocation, attributeNesting)
                ?: throw IllegalArgumentException("Not found: $objectLocation.$attributeNesting")

        return scalarParameter.asString()
            ?: throw IllegalArgumentException("Expected string ($objectLocation.$attributeNesting): $scalarParameter")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun withNewBundle(
            projectPath: BundlePath,
            packageNotation: BundleNotation
    ): GraphNotation {
        check(! bundleNotations.values.containsKey(projectPath)) {"Already exists: $projectPath"}

        val buffer = mutableMapOf<BundlePath, BundleNotation>()

        buffer.putAll(bundleNotations.values)

        buffer[projectPath] = packageNotation

        return GraphNotation(BundleTree(buffer))
    }


    fun withModifiedBundle(
            projectPath: BundlePath,
            packageNotation: BundleNotation
    ): GraphNotation {
        check(bundleNotations.values.containsKey(projectPath)) {"Not found: $projectPath"}

        val buffer = mutableMapOf<BundlePath, BundleNotation>()

        for (e in bundleNotations.values) {
            buffer[e.key] =
                    if (e.key == projectPath) {
                        packageNotation
                    }
                    else {
                        e.value
                    }
        }

        return GraphNotation(BundleTree(buffer))
    }


    fun withoutBundle(
            projectPath: BundlePath
    ): GraphNotation {
        check(bundleNotations.values.containsKey(projectPath)) {"Already absent: $projectPath"}

        val buffer = mutableMapOf<BundlePath, BundleNotation>()

        for (e in bundleNotations.values) {
            if (e.key == projectPath) {
                continue
            }

            buffer[e.key] = e.value
        }

        return GraphNotation(BundleTree(buffer))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun filterPaths(predicate: (BundlePath) -> Boolean): GraphNotation {
        val filteredBundle = mutableMapOf<BundlePath, BundleNotation>()

        for (e in bundleNotations.values) {
            if (! predicate.invoke(e.key)) {
                continue
            }

            filteredBundle[e.key] = e.value
        }

        return GraphNotation(BundleTree(filteredBundle))
    }
}