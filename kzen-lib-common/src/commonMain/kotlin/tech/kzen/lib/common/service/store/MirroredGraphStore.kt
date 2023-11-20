package tech.kzen.lib.common.service.store

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent


@Suppress("unused")
class MirroredGraphStore(
    private val localGraphStore: DirectGraphStore,
    private val remoteGraphStore: RemoteGraphStore
):
    LocalGraphStore
{
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


    private suspend fun publishSuccess(
        event: NotationEvent,
        attachment: LocalGraphStore.Attachment
    ) {
        val graphDefinition = localGraphStore.graphDefinition()

        for (observer in observers) {
            observer.onCommandSuccess(event, graphDefinition, attachment)
        }
    }


    private suspend fun publishFailure(
        command: NotationCommand,
        cause: Throwable,
        attachment: LocalGraphStore.Attachment
    ) {
        for (observer in observers) {
            observer.onCommandFailure(command, cause, attachment)
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
    suspend fun apply(
        command: NotationCommand,
        attachment: LocalGraphStore.Attachment = LocalGraphStore.Attachment.empty
    ):
        MirroredGraphResult
    {
        return coroutineScope {
            var remoteError: Throwable? = null
            val remoteDigestAsync = async {
                try {
                    val result = remoteGraphStore.apply(command)
                    result
                }
                catch (e: Throwable) {
                    publishFailure(command, e, attachment)
                    remoteError = e
                    null
                }
            }

            var localError: Throwable? = null
            val localEventAsync = async {
                try {
                    val result = localGraphStore.apply(command)
                    publishSuccess(result, attachment)
                    result
                }
                catch (e: Throwable) {
                    publishFailure(command, e, attachment)
                    localError = e
                    null
                }
            }

            val localEvent = localEventAsync.await()
            val localDigest = localGraphStore.digest()

            val remoteDigest = remoteDigestAsync.await()

            when {
                remoteError != null -> {
                    MirroredGraphError(remoteError!!, true)
                }

                localError != null -> {
                    MirroredGraphError(localError!!, false)
                }

                localDigest != remoteDigest -> {
                    localGraphStore.refresh()
                    publishRefresh()
                    MirroredGraphSuccess(localEvent!!, true)
                }

                else -> {
                    MirroredGraphSuccess(localEvent!!, false)
                }
            }
        }
    }
}