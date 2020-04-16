package tech.kzen.lib.common.model.structure.notation.cqrs

import tech.kzen.lib.common.model.structure.notation.GraphNotation


data class NotationTransition(
        val notationEvent: NotationEvent,
        val graphNotation: GraphNotation
)