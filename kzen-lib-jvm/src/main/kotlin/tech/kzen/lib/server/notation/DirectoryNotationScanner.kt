package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.scan.NotationScanner


class DirectoryNotationScanner : NotationScanner {
    override suspend fun scan(): List<ProjectPath> {
        TODO()
//        return listOf(
//                ProjectPath("notation.yaml"))
    }
}