package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.io.flat.media.NotationMedia


class ClasspathNotationMedia : NotationMedia {
    override suspend fun read(location: ProjectPath): ByteArray {
//        val classpath = "/" + location.relativeLocation
//        return javaClass.getResource(classpath).readBytes()

        println("ClasspathNotationMedia - location.relativeLocation: ${location.relativeLocation}")

        val loader = Thread.currentThread().contextClassLoader
        return loader.getResource(location.relativeLocation).readBytes()
    }


    override suspend fun write(location: ProjectPath, bytes: ByteArray) {
        throw UnsupportedOperationException("Classpath writing not supported")
    }
}