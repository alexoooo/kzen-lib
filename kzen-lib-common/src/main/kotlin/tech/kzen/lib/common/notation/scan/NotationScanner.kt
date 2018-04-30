package tech.kzen.lib.common.notation.scan

import tech.kzen.lib.common.notation.model.ProjectPath


interface NotationScanner {
    suspend fun scan(): List<ProjectPath>
}