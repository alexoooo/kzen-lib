package tech.kzen.lib.server.notation.locate

import tech.kzen.lib.common.notation.model.ProjectPath
import java.nio.file.Path


interface FileNotationLocator {
    fun primaryRoot(): Path

    fun locateExisting(location: ProjectPath): Path
}