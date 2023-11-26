package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.util.digest.Digest


interface RemoteGraphStore {
    suspend fun apply(command: NotationCommand): Digest
}