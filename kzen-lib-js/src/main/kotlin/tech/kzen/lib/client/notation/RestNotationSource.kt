package tech.kzen.lib.client.notation

import tech.kzen.lib.client.notation.NotationRestApi.httpGet
import tech.kzen.lib.common.notation.model.ProjectPath
import tech.kzen.lib.common.notation.read.flat.source.NotationSource


class RestNotationSource(
        private val baseUrl: String
) : NotationSource {
    override suspend fun read(location: ProjectPath): ByteArray {
        val response = httpGet("$baseUrl/notation/${location.relativeLocation}")

        // from kotlinx.serialization String.toUtf8Bytes
        val blck = js("unescape(encodeURIComponent(response))")
        return (blck as String).toList().map { it.toByte() }.toByteArray()
    }
}