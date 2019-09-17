package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent


class MirroredGraphStore(
        private val localGraphStore: LocalGraphStore,
        private val remoteGraphStore: RemoteGraphStore
) {
    //-----------------------------------------------------------------------------------------------------------------
    interface Observer {
        suspend fun onCommandFailure(
                command: NotationCommand,
                cause: Throwable)

        suspend fun onCommandSuccess(
                event: NotationEvent,
                graphDefinition: GraphDefinition)

        suspend fun onStoreRefresh(
                graphDefinition: GraphDefinition)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val observers = mutableSetOf<Observer>()


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun observe(observer: Observer) {
        observers.add(observer)

//        if (mostRecent != null) {
//            subscriber.handleModel(mostRecent!!, null)
//        }

        observer.onStoreRefresh(
                localGraphStore.graphDefinition())
    }


    fun unobserve(observer: Observer) {
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