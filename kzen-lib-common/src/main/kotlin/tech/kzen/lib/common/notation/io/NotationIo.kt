package tech.kzen.lib.common.notation.io

import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectPath


interface NotationIo {
    suspend fun read(location: ProjectPath): PackageNotation
    
    suspend fun write(location: ProjectPath, notation: PackageNotation)
}