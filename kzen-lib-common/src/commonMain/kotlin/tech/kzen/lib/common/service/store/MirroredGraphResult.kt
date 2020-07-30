package tech.kzen.lib.common.service.store

import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent


sealed class MirroredGraphResult


data class MirroredGraphError(
    val error: Throwable,
    val remote: Boolean
): MirroredGraphResult()


data class MirroredGraphSuccess(
    val event: NotationEvent,
    val refreshed: Boolean
): MirroredGraphResult()

