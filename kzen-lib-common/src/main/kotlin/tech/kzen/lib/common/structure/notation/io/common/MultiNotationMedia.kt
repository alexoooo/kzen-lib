package tech.kzen.lib.common.structure.notation.io.common

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentPathMap
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.structure.notation.io.NotationMedia
import tech.kzen.lib.common.structure.notation.io.model.DocumentScan
import tech.kzen.lib.common.structure.notation.io.model.NotationScan
import tech.kzen.lib.platform.collect.toPersistentMap


class MultiNotationMedia(
        private val media: List<NotationMedia>
) : NotationMedia {
    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun scan(): NotationScan {
        val all = mutableMapOf<DocumentPath, DocumentScan>()
        for (delegate in media) {
            all.putAll(delegate.scan().documents.values)
        }
        return NotationScan(DocumentPathMap(all.toPersistentMap()))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readDocument(documentPath: DocumentPath): ByteArray {
        for (source in media) {
            try {
                return source.readDocument(documentPath)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Not found in $source - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to read: $documentPath")
    }


    override suspend fun writeDocument(documentPath: DocumentPath, contents: ByteArray) {
        for (medium in media) {
            try {
                return medium.writeDocument(documentPath, contents)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't write in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to write: $documentPath")
    }


    override suspend fun deleteDocument(documentPath: DocumentPath) {
        for (medium in media) {
            try {
                return medium.deleteDocument(documentPath)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't delete in $medium - " +
                        "${ignored::class.simpleName} - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to delete: $documentPath")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun readResource(resourceLocation: ResourceLocation): ByteArray {
        for (source in media) {
            try {
                return source.readResource(resourceLocation)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Not found in $source - " +
                        "${ignored::class.simpleName} - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to read: $resourceLocation")
    }


    override suspend fun writeResource(resourceLocation: ResourceLocation, contents: ByteArray) {
        for (medium in media) {
            try {
                return medium.writeResource(resourceLocation, contents)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't write in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to write: $resourceLocation")
    }


    override suspend fun deleteResource(resourceLocation: ResourceLocation) {
        for (medium in media) {
            try {
                return medium.deleteResource(resourceLocation)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't delete in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to delete: $resourceLocation")
    }
}