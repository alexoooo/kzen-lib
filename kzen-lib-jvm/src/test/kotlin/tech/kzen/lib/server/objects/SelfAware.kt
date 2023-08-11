package tech.kzen.lib.server.objects

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.reflect.Reflect


@Reflect
class SelfAware(
        val objectLocation: ObjectLocation,
        val objectNotation: ObjectNotation,
        val documentNotation: DocumentNotation
)