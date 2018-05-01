package tech.kzen.lib.common.notation.scan

import tech.kzen.lib.common.notation.model.ProjectPath


class LiteralNotationScanner(
        private val paths: List<String>
): NotationScanner {
    override suspend fun scan(): List<ProjectPath> {
        return paths.map { ProjectPath(it) }
    }
}