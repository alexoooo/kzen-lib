//package tech.kzen.lib.server.notation.scan
//
//import tech.kzen.lib.common.notation.model.ProjectPath
//import tech.kzen.lib.common.notation.scan.NotationScanner
//import tech.kzen.lib.common.util.Digest
//import tech.kzen.lib.server.notation.FileNotationMedia
//import tech.kzen.lib.server.notation.locate.FileNotationLocator
//import java.nio.file.*
//import java.nio.file.attribute.BasicFileAttributes
//import java.time.Instant
//
//
//class DirectoryNotationScanner(
//        private var locator: FileNotationLocator,
//        private var media: FileNotationMedia
//) : NotationScanner {
//    //-----------------------------------------------------------------------------------------------------------------
//    private val digestCache: MutableMap<ProjectPath, TimedDigest> = mutableMapOf()
//
//    private data class TimedDigest(
//            var modified: Instant,
//            var digest: Digest)
//
//
//    private suspend fun digest(
//            path: ProjectPath
//    ): Digest {
//        val bytes = media.read(path)
//        return Digest.ofXoShiRo256StarStar(bytes)
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override suspend fun scan(): Map<ProjectPath, Digest> {
//        val root = locator.primaryRoot()
//
//        val locationTimes = mutableMapOf<ProjectPath, Instant>()
//        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
//            override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
//                if (file!!.fileName.toString().endsWith(".yaml")) {
//                    val path = ProjectPath(root.relativize(file).toString())
//                    val modified = attrs!!.lastModifiedTime().toInstant()
//
//                    locationTimes[path] = modified
//                }
//
//                return FileVisitResult.CONTINUE
//            }
//        })
//
//        val digested = mutableMapOf<ProjectPath, Digest>()
//
//        for (e in locationTimes) {
//            val timedDigest = digestCache[e.key]
//
//            if (timedDigest == null) {
//                val digest = digest(e.key)
//                digestCache[e.key] = TimedDigest(e.value, digest)
//                digested[e.key] = digest
//            }
//            else {
//                if (timedDigest.modified != e.value) {
//                    timedDigest.digest = digest(e.key)
//                    timedDigest.modified = e.value
//                }
//
//                digested[e.key] = timedDigest.digest
//            }
//        }
//
//        return digested
//    }
//}