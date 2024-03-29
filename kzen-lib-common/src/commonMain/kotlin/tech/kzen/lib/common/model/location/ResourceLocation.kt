package tech.kzen.lib.common.model.location

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.resource.ResourcePath


data class ResourceLocation(
    val documentPath: DocumentPath,
    val resourcePath: ResourcePath
)