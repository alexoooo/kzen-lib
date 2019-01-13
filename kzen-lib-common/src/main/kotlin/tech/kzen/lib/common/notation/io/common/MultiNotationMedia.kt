package tech.kzen.lib.common.notation.io.common

import tech.kzen.lib.common.notation.io.NotationMedia
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.BundleTree
import tech.kzen.lib.common.util.Digest


class MultiNotationMedia(
        private val media: List<NotationMedia>
) : NotationMedia {
    override suspend fun scan(): BundleTree<Digest> {
        val all = mutableMapOf<BundlePath, Digest>()
        for (delegate in media) {
            all.putAll(delegate.scan().values)
        }
        return BundleTree(all)
    }


    override suspend fun read(location: BundlePath): ByteArray {
        for (source in media) {
            try {
                return source.read(location)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Not found in $source - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to read: $location")
    }


    override suspend fun write(location: BundlePath, bytes: ByteArray) {
        for (medium in media) {
            try {
                return medium.write(location, bytes)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't write in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to write: $location")
    }


    override suspend fun delete(location: BundlePath) {
        for (medium in media) {
            try {
                return medium.delete(location)
            }
            catch (ignored: Exception) {
                println("MultiNotationMedia - Can't delete in $medium - ${ignored.message}")
            }
        }

        throw IllegalArgumentException("Unable to delete: $location")
    }
}