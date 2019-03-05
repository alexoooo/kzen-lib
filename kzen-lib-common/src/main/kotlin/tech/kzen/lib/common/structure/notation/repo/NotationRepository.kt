package tech.kzen.lib.common.structure.notation.repo

import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.context.GraphDefiner
import tech.kzen.lib.common.structure.GraphStructure
import tech.kzen.lib.common.structure.metadata.read.NotationMetadataReader
import tech.kzen.lib.common.structure.notation.edit.*
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.NotationParser
import tech.kzen.lib.common.structure.notation.model.BundleNotation
import tech.kzen.lib.common.structure.notation.model.GraphNotation
import tech.kzen.lib.common.util.Cache
import tech.kzen.lib.common.util.Digest


// TODO: threadsafe?
class NotationRepository(
        private val notationMedia: NotationMedia,
        private val notationParser: NotationParser,
        private val metadataReader: NotationMetadataReader
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

            val bundleNotation = notationParser.parseBundle(body)
            packages[projectPath] = bundleNotation
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
    suspend fun apply(command: NotationCommand): NotationEvent {
        val notation = notation()
        val aggregate = aggregate()

        val oldBundles = notation.bundles

        val event =
                when (command) {
                    is StructuralNotationCommand ->
                        aggregate.apply(command)

                    is SemanticNotationCommand -> {
                        val graphMetadata = metadataReader.read(notation)
                        val graphDefinition = GraphDefiner.define(GraphStructure(notation, graphMetadata))
                        aggregate.apply(command, graphDefinition)
                    }
                }


        val newBundles = aggregate.state.bundles

        var writtenAny = false
        for (updatedBundle in newBundles.values) {
            if (oldBundles.values.containsKey(updatedBundle.key) &&
                    updatedBundle.value.objects.equalsInOrder(oldBundles.values[updatedBundle.key]!!.objects)) {
                continue
            }

            val written = writeIfRequired(updatedBundle.key, updatedBundle.value)
            writtenAny = writtenAny || written
        }
        for (removed in oldBundles.values.keys.minus(newBundles.values.keys)) {
            delete(removed)
            writtenAny = true
        }

        if (writtenAny) {
            // TODO: avoid needless clearing
            clearCache()
        }

        return event
    }


    private suspend fun writeIfRequired(
            bundlePath: BundlePath,
            packageNotation: BundleNotation
    ): Boolean {
        val cachedDigest = scanCache[bundlePath]

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
//        println("!!! updatedBody: ${IoUtils.utf8ToString(updatedBody)}")

        if (updatedBody.contentEquals(previousBody) && ! previousMissing) {
            return false
        }

        notationMedia.write(bundlePath, updatedBody)
        scanCache[bundlePath] = Digest.ofXoShiRo256StarStar(updatedBody)

        return true
    }


    private suspend fun delete(
            bundlePath: BundlePath
    ) {
        notationMedia.delete(bundlePath)
        scanCache.remove(bundlePath)
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