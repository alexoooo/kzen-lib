package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent


@Suppress("unused")
class MirroredGraphStore(
        private val localGraphStore: DirectGraphStore,
        private val remoteGraphStore: RemoteGraphStore
): LocalGraphStore {
    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<LocalGraphStore.Observer>()


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun observe(observer: LocalGraphStore.Observer) {
        observers.add(observer)

        observer.onStoreRefresh(
                localGraphStore.graphDefinition())
    }


    override fun unobserve(observer: LocalGraphStore.Observer) {
        observers.remove(observer)
    }


    private suspend fun publishSuccess(event: NotationEvent) {
        val graphDefinition = localGraphStore.graphDefinition()

        for (observer in observers) {
            observer.onCommandSuccess(event, graphDefinition)
        }
    }


    private suspend fun publishFailure(command: NotationCommand, cause: Throwable) {
        for (observer in observers) {
            observer.onCommandFailure(command, cause)
        }
    }


    private suspend fun publishRefresh() {
        val graphDefinition = localGraphStore.graphDefinition()

        for (observer in observers) {
            observer.onStoreRefresh(graphDefinition)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun graphNotation(): GraphNotation {
        return localGraphStore.graphNotation()
    }


    override suspend fun graphStructure(): GraphStructure {
        return localGraphStore.graphStructure()
    }


    override suspend fun graphDefinition(): GraphDefinitionAttempt {
        return localGraphStore.graphDefinition()
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(command: NotationCommand) {
        // NB: for now, this has to happen before clientEvent for VisualDataflowProvider.inspectVertex
        // TODO: make this parallel with client processing via VisualDataflowProvider.initialVertexState
        val remoteDigest = try {
            remoteGraphStore.apply(command)
        }
        catch (e: Throwable) {
            publishFailure(command, e)
            return
        }

        val localEvent = try {
            localGraphStore.apply(command)
        }
        catch (e: Throwable) {
            publishFailure(command, e)
            return
        }

        val localDigest = localGraphStore.digest()

        if (localDigest != remoteDigest) {
            localGraphStore.refresh()
            publishRefresh()
        }
        else {
            publishSuccess(localEvent)
        }
    }
}