package tech.kzen.lib.server.notation.locate

import tech.kzen.lib.common.model.document.DocumentPath
import java.nio.file.Path


interface FileNotationLocator {
    fun scanRoots(): List<Path>

    fun locateExisting(location: DocumentPath): Path?

    fun resolveNew(location: DocumentPath): Path?
}