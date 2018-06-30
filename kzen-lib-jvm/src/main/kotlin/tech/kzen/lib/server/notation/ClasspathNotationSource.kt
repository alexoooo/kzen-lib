package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia


class ClasspathNotationSource : NotationMedia {
    override suspend fun read(location: ProjectPath): ByteArray {
//        val classpath = "/" + location.relativeLocation
//        return javaClass.getResource(classpath).readBytes()
        val loader = Thread.currentThread().contextClassLoader
        return loader.getResource(location.relativeLocation).readBytes()
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }
}