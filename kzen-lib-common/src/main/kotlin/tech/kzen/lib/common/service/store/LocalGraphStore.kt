package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent


interface LocalGraphStore {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun onCommandSuccess(
                event: NotationEvent,
                graphDefinition: GraphDefinition)

        suspend fun onCommandFailure(
                command: NotationCommand,
                cause: Throwable)

        suspend fun onStoreRefresh(
                graphDefinition: GraphDefinition)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(observer: Observer)

    fun unobserve(observer: Observer)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun graphNotation(): GraphNotation

    suspend fun graphStructure(): GraphStructure

    suspend fun graphDefinition(): GraphDefinition
}