package tech.kzen.lib.common.structure.notation.io.model

import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.structure.notation.io.model.DocumentScan


data class NotationScan(
        val documents: DocumentPathMap<DocumentScan>
)