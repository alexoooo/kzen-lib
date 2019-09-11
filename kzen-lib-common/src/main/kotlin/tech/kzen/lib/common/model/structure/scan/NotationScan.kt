package tech.kzen.lib.common.model.structure.scan

import tech.kzen.lib.common.model.document.DocumentPathMap


data class NotationScan(
        val documents: DocumentPathMap<DocumentScan>
)