package tech.kzen.lib.common.notation.repo

import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.edit.NotationCommand
import tech.kzen.lib.common.notation.edit.NotationEvent
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.NotationTree
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.util.Cache
import tech.kzen.lib.common.util.Digest


// TODO: threadsafe?
class NotationRepository(
        private val notationMedia: NotationMedia,
        private val notationParser: NotationParser
) {
    //-----------------------------------------------------------------------------------------------------------------
    private var scanCache = mutableMapOf<BundlePath, Digest>()

    // TODO: use notation from inside ProjectAggregate?
    private var projectNotationCache: NotationTree? = null
    private var projectAggregateCache: NotationAggregate? = null
    private var fileCache = Cache<ByteArray>(10)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun notation(): NotationTree {
        if (projectNotationCache == null) {
            projectNotationCache = read()
        }
        return projectNotationCache!!
    }


    private suspend fun read(): NotationTree {
        val packageBytes = mutableMapOf<BundlePath, ByteArray>()
        val packages = mutableMapOf<BundlePath, BundleNotation>()

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

        return NotationTree(BundleTree(packages))
    }


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun aggregate(): NotationAggregate {
        if (projectAggregateCache == null) {
            projectAggregateCache = NotationAggregate(notation())
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
    suspend fun apply(command: NotationCommand): NotationEvent {
        val oldPackages = notation().files

        val event = aggregate().apply(command)
        val newPackages = aggregate().state.files

        var writtenAny = false
        for (updatedPackage in newPackages.values) {
            if (oldPackages.values.containsKey(updatedPackage.key) &&
                    updatedPackage.value.objects.equalsInOrder(oldPackages.values[updatedPackage.key]!!.objects)) {
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
            projectPath: BundlePath,
            packageNotation: BundleNotation
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
            subCombiner.add(Digest.ofXoShiRo256StarStar(e.key.asString()))
            subCombiner.add(e.value)

            combiner.add(subCombiner.combine())

            subCombiner.clear()
        }

        return combiner.combine()
    }
}