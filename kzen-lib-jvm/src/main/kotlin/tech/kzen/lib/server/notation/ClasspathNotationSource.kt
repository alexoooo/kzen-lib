package tech.kzen.lib.server.notation

import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.flat.source.NotationSource


class ClasspathNotationSource : NotationSource {
    override suspend fun read(location: ProjectPath): ByteArray {
//        val classpath = "/" + location.relativeLocation
//        return javaClass.getResource(classpath).readBytes()
        val loader = Thread.currentThread().contextClassLoader
        return loader.getResource(location.relativeLocation).readBytes()
    }
}