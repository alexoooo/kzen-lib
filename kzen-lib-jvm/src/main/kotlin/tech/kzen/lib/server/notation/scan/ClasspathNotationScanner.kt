//package tech.kzen.lib.server.notation.scan
//
//import com.google.common.reflect.ClassPath
//import tech.kzen.lib.common.notation.model.ProjectPath
//import tech.kzen.lib.common.notation.scan.NotationScanner
//import tech.kzen.lib.common.util.Digest
//import tech.kzen.lib.server.notation.ClasspathNotationMedia
//
//
//class ClasspathNotationScanner(
//        private var media: ClasspathNotationMedia,
//        private var prefix: String = "notation/",
//        private var suffix: String = ".yaml",
//        private var loader: ClassLoader = Thread.currentThread().contextClassLoader
//): NotationScanner {
//    //-----------------------------------------------------------------------------------------------------------------
//    private val cache: MutableMap<ProjectPath, Digest> = mutableMapOf()
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun scan(): Map<ProjectPath, Digest> {
//        if (cache.isEmpty()) {
//            val paths = scanPaths()
//
//            for (path in paths) {
//                val bytes = media.read(path)
//                val digest = Digest.ofXoShiRo256StarStar(bytes)
//                cache[path] = digest
//            }
//        }
//        return cache
//    }
//
//
//    private fun scanPaths(): List<ProjectPath> {
//        return ClassPath
//                .from(loader)
//                .resources
//                .filter {
//                    it.resourceName.startsWith(prefix) &&
//                    it.resourceName.endsWith(suffix) &&
//                    ProjectPath.matches(it.resourceName)
//                }
//                .map{ ProjectPath(it.resourceName) }
//                .toList()
//    }
//}