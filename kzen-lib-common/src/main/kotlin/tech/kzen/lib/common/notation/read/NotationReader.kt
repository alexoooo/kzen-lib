package tech.kzen.lib.common.notation.read

import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectPath


interface NotationReader {
    suspend fun read(location: ProjectPath): PackageNotation
}