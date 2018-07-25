package tech.kzen.lib.server.notation.scan

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.scan.NotationScanner
import tech.kzen.lib.server.notation.locate.FileNotationLocator
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors


class DirectoryNotationScanner(
//        private var root: Path
        private var locator: FileNotationLocator
) : NotationScanner {
    override suspend fun scan(): List<ProjectPath> {
        val root = locator.primaryRoot()

        val paths = Files.walk(root).use {
            it.filter {
                it.fileName.toString().endsWith(".yaml")
            }.map {
                root.relativize(it)
            }.collect(Collectors.toList())
        }

        return paths
                .map { ProjectPath(it.toString()) }
    }
}