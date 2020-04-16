package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent


interface LocalGraphStore {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun onCommandSuccess(
                event: NotationEvent,
                graphDefinition: GraphDefinitionAttempt)

        suspend fun onCommandFailure(
                command: NotationCommand,
                cause: Throwable)

        suspend fun onStoreRefresh(
                graphDefinition: GraphDefinitionAttempt)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(observer: Observer)

    fun unobserve(observer: Observer)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun graphNotation(): GraphNotation

    suspend fun graphStructure(): GraphStructure

    suspend fun graphDefinition(): GraphDefinitionAttempt
}