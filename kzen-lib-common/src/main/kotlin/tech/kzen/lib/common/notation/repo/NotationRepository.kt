package tech.kzen.lib.common.notation.repo

import tech.kzen.lib.common.edit.ProjectAggregate
import tech.kzen.lib.common.edit.ProjectCommand
import tech.kzen.lib.common.edit.ProjectEvent
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.model.PackageNotation
import tech.kzen.lib.common.notation.model.ProjectNotation
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.util.Cache
import tech.kzen.lib.common.util.Digest


// TODO: threadsafe?
class NotationRepository(
        private val notationMedia: NotationMedia,
        private val notationParser: NotationParser
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache = mutableMapOf<ProjectPath, Digest>()

    // TODO: use notation from inside ProjectAggregate?
    private var projectNotationCache: ProjectNotation? = null
    private var projectAggregateCache: ProjectAggregate? = null
    private var fileCache = Cache<ByteArray>(10)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun notation(): ProjectNotation {
        if (projectNotationCache == null) {
            projectNotationCache = read()
        }
        return projectNotationCache!!
    }


    private suspend fun read(): ProjectNotation {
        val packageBytes = mutableMapOf<ProjectPath, ByteArray>()
        val packages = mutableMapOf<ProjectPath, PackageNotation>()

        scanCache.clear()
        scanCache.putAll(notationMedia.scan())

        if (scanCache.size * 2 > fileCache.size) {
            fileCache.size = scanCache.size * 2
        }

        for (e in scanCache) {
            val projectPath = e.key

            val bodyCache = fileCache.get(e.value)

            val body = bodyCache
                    ?: notationMedia.read(projectPath)

            packageBytes[projectPath] = body

            val packageNotation = notationParser.parsePackage(body)
            packages[projectPath] = packageNotation
        }

        return ProjectNotation(packages)
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun aggregate(): ProjectAggregate {
        if (projectAggregateCache == null) {
            projectAggregateCache = ProjectAggregate(notation())
        }
        return projectAggregateCache!!
    }



    //-----------------------------------------------------------------------------------------------------------------
    fun clearCache() {
        scanCache.clear()
        projectNotationCache = null
        projectAggregateCache = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun apply(command: ProjectCommand): ProjectEvent {
        val oldPackages = notation().packages

        val event = aggregate().apply(command)
        val newPackages = aggregate().state.packages

        var writtenAny = false
        for (updatedPackage in newPackages) {
            if (oldPackages.containsKey(updatedPackage.key) &&
                    updatedPackage.value.equalsInOrder(oldPackages[updatedPackage.key]!!)) {
                continue
            }

            val written = writeIfRequired(updatedPackage.key, updatedPackage.value)
            writtenAny = writtenAny || written
        }

        if (writtenAny) {
            // TODO: avoid needless clearing
            clearCache()
        }

        return event
    }


    private suspend fun writeIfRequired(
            projectPath: ProjectPath,
            packageNotation: PackageNotation
    ): Boolean {
        val cachedDigest = scanCache[projectPath]

        var previousMissing = false
        val previousBody: ByteArray =
                if (cachedDigest == null) {
                    previousMissing = true
                    ByteArray(0)
                }
                else {
                    val cached = fileCache.get(cachedDigest)
                    if (cached == null) {
                        previousMissing = true
                        ByteArray(0)
                    }
                    else {
                        cached
                    }
                }

        val updatedBody = notationParser.deparsePackage(packageNotation, previousBody)

        if (updatedBody.contentEquals(previousBody) && ! previousMissing) {
            return false
        }

        notationMedia.write(projectPath, updatedBody)
        scanCache[projectPath] = Digest.ofXoShiRo256StarStar(updatedBody)

        return true
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun digest(): Digest {
        if (scanCache.isEmpty()) {
            read()
        }

        val combiner = Digest.UnorderedCombiner()
        val subCombiner = Digest.OrderedCombiner()

        for (e in scanCache) {
            subCombiner.add(Digest.ofXoShiRo256StarStar(e.key.relativeLocation))
            subCombiner.add(e.value)

            combiner.add(subCombiner.combine())

            subCombiner.clear()
        }

        return combiner.combine()
    }
}