package tech.kzen.lib.common.context

import tech.kzen.lib.common.api.ObjectCreator
import tech.kzen.lib.common.api.ObjectDefiner
import tech.kzen.lib.common.definition.GraphDefinition
import tech.kzen.lib.common.definition.ObjectDefinition
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ScalarParameterNotation
import tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectCreator
import tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectDefiner


object ObjectGraphDefiner {
    //-----------------------------------------------------------------------------------------------------------------
    val bootstrapObjects = mapOf(
            DefaultConstructorObjectCreator::class.simpleName!! to DefaultConstructorObjectCreator,
            DefaultConstructorObjectDefiner::class.simpleName!! to DefaultConstructorObjectDefiner)


    fun define(
            projectNotation: ProjectNotation,
            projectMetadata: GraphMetadata
    ): GraphDefinition {
        val definerAndRelatedInstances = mutableMapOf<String, Any>()

        definerAndRelatedInstances.putAll(bootstrapObjects)

        val openDefinitions = projectNotation
                .objectNames
                .filter {
                    ! bootstrapObjects.containsKey(it) &&
                            ! isAbstract(it, projectNotation)
                }.toMutableSet()

        val closedDefinitions = mutableMapOf<String, ObjectDefinition>()
        val missingInstances = mutableSetOf<String>()

        val levelClosed = mutableSetOf<String>()
        val levelCreated = mutableSetOf<String>()
        val missingCreatorInstances = mutableSetOf<String>()

        var levelCount = 0
        while (! openDefinitions.isEmpty()) {
            levelCount += 1
            check(levelCount < 10, {"too deep"})
            println("^^^^^ open - $levelCount: $openDefinitions")

            for (objectName in openDefinitions) {
                println("^^^^^ objectName: $objectName")

                val definerName = definerName(objectName, projectNotation)
                val definer = definerAndRelatedInstances[definerName] as? ObjectDefiner

                if (definer == null) {
                    missingInstances.add(definerName)
                    continue
                }

                val definition = definer.define(
                        objectName,
                        projectNotation,
                        projectMetadata,
                        GraphDefinition(closedDefinitions),
                        ObjectGraph(definerAndRelatedInstances))
                println("  >> definition: $definition")

                if (definition.isError()) {
                    println(" !! definition error: ${definition.errorMessage}")

                    missingInstances.addAll(definition.missingObjects)
                    continue
                }

                closedDefinitions[objectName] = definition.value!!
                levelClosed.add(objectName)
            }

            println("--- missingInstances: $missingInstances")
            for (missingName in missingInstances) {
                val definition =
                        closedDefinitions[missingName]
                                ?: continue

                println("  $$ got definition for: $missingName")

                var hasMissingCreatorInstances = false
                if (! definerAndRelatedInstances.containsKey(definition.creator)) {
                    missingCreatorInstances.add(definition.creator)
                    hasMissingCreatorInstances = true

                    println("  $$ missing creator ($missingName): ${definition.creator}")
                }

                for (creatorReference in definition.creatorReferences) {
                    if (! definerAndRelatedInstances.containsKey(creatorReference)) {
                        missingCreatorInstances.add(creatorReference)
                        hasMissingCreatorInstances = true

                        println("  $$ missing creator reference ($missingName): " +
                                "${definition.creator} - $creatorReference")
                    }
                }

                if (hasMissingCreatorInstances) {
                    continue
                }

                val creator = definerAndRelatedInstances[definition.creator] as ObjectCreator

                val newInstance = creator.create(
                        definition,
                        projectMetadata.objectMetadata[missingName]!!,
                        ObjectGraph(definerAndRelatedInstances))

                println("  $$ created: $missingName")

                definerAndRelatedInstances[missingName] = newInstance
                levelCreated.add(missingName)
            }

            missingInstances.addAll(missingCreatorInstances)
            missingInstances.removeAll(levelCreated)

            check(levelClosed.isNotEmpty() || levelCreated.isNotEmpty(),
                    {"Graph cycle?"})
            openDefinitions.removeAll(levelClosed)

            levelCreated.clear()
            levelClosed.clear()
            missingCreatorInstances.clear()
        }
        return GraphDefinition(closedDefinitions)
    }



    private fun definerName(
            objectName: String,
            projectNotation: ProjectNotation
    ): String = (
            projectNotation.transitiveParameter(
                    objectName, "definer"
            )!! as ScalarParameterNotation
    ).value as String



    private fun isAbstract(
            objectName: String,
            projectNotation: ProjectNotation
    ): Boolean {
        val abstractParameter =
                projectNotation.directParameter(objectName, "abstract")
                        ?: return false

        return (abstractParameter as ScalarParameterNotation).value as Boolean
    }
}