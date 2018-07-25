package tech.kzen.lib.server.notation.scan

import com.google.common.reflect.ClassPath
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.scan.NotationScanner


class ClasspathNotationScanner(
        private var prefix: String = "notation/",
        private var suffix: String = ".yaml",
        private var classLoader: ClassLoader = Thread.currentThread().contextClassLoader
): NotationScanner {
    override suspend fun scan(): List<ProjectPath> {
        return ClassPath
                .from(classLoader)
                .resources
                .filter {
                    it.resourceName.startsWith(prefix) &&
                    it.resourceName.endsWith(suffix) &&
                    ProjectPath.matches(it.resourceName) //&&
//                    it.resourceName.startsWith(prefix) &&
//                    ! it.resourceName.endsWith(".class")
                }
//                .map { it.resourceName.replace('/', '.') }
                .map{ ProjectPath(it.resourceName) }
                .toList()
    }
}