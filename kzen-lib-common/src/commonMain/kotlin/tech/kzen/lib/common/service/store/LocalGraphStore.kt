package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent


interface LocalGraphStore {
    //-----------------------------------------------------------------------------------------------------------------
    data class Attachment(
        val header: Map<String, Any>
    ) {
        companion object {
            val empty = Attachment(mapOf())
        }
    }


    interface Observer {
        suspend fun onCommandSuccess(
            event: NotationEvent,
            graphDefinition: GraphDefinitionAttempt,
            attachment: Attachment
        )

        suspend fun onCommandFailure(
            command: NotationCommand,
            cause: Throwable,
            attachment: Attachment)

        suspend fun onStoreRefresh(
            graphDefinitionAttempt: GraphDefinitionAttempt)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(observer: Observer)

    fun unobserve(observer: Observer)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun graphNotation(): GraphNotation

    suspend fun graphStructure(): GraphStructure

    /**
     * // TODO: rename graphDefinitionAttempt
     * Implementations may cache per notation version (keyed by content digest), so repeat calls
     * against unchanged notation can return the same attempt instance — see [DirectGraphStore].
     */
    suspend fun graphDefinition(): GraphDefinitionAttempt
}