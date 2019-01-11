package tech.kzen.lib.server.notation.locate

import tech.kzen.lib.common.api.model.BundlePath
import java.nio.file.Path


interface FileNotationLocator {
    fun scanRoots(): List<Path>

    fun locateExisting(location: BundlePath): Path?

    fun resolveNew(location: BundlePath): Path?
}