package tech.kzen.lib.common.notation.repo

import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.notation.edit.NotationAggregate
import tech.kzen.lib.common.notation.edit.NotationEvent
import tech.kzen.lib.common.notation.edit.StructuralNotationCommand
import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.notation.io.NotationParser
import tech.kzen.lib.common.notation.model.BundleNotation
import tech.kzen.lib.common.notation.model.GraphNotation
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
    private var projectNotationCache: GraphNotation? = null
    private var projectAggregateCache: NotationAggregate? = null
    private var fileCache = Cache<ByteArray>(10)


    //-----------------------------------------------------------------------------------------------------------------
    suspend fun notation(): GraphNotation {
        if (projectNotationCache == null) {
            projectNotationCache = read()
        }
        return projectNotationCache!!
    }


    private suspend fun read(): GraphNotation {
        val packageBytes = mutableMapOf<BundlePath, ByteArray>()
        val packages = mutableMapOf<BundlePath, BundleNotation>()

        scanCache.clear()
        scanCache.putAll(notationMedia.scan().values)

        if (scanCache.size * 2 > fileCache.size) {
            fileCache.size = scanCache.size * 2
        }

        for (e in scanCache) {
            val projectPath = e.key

            val bodyCache = fileCache.get(e.value)

            val body = bodyCache
                    ?: notationMedia.read(projectPath)

            packageBytes[projectPath] = body

            val packageNotation = notationParser.parseBundle(body)
            packages[projectPath] = packageNotation
        }

        return GraphNotation(BundleTree(packages))
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
    suspend fun apply(command: StructuralNotationCommand): NotationEvent {
        val oldBundles = notation().bundleNotations

        val event = aggregate().apply(command)
        val newBundles = aggregate().state.bundleNotations

        var writtenAny = false
        for (updatedBundle in newBundles.values) {
            if (oldBundles.values.containsKey(updatedBundle.key) &&
                    updatedBundle.value.objects.equalsInOrder(oldBundles.values[updatedBundle.key]!!.objects)) {
                continue
            }

            val written = writeIfRequired(updatedBundle.key, updatedBundle.value)
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

        val updatedBody = notationParser.deparseBundle(packageNotation, previousBody)

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