package tech.kzen.lib.server.notation

import kotlinx.coroutines.runBlocking
import tech.kzen.lib.common.model.document.DocumentPath
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class OriginNotationMediaTest {
    private val alphaBody = "Alpha:\n  is: Thing\n"
    private val betaBody = "Beta:\n  is: Thing\n"


    @Test
    fun scansOnlyTheGivenJarsAndReadsThroughTheExactEntry() {
        runBlocking { scansOnlyTheGivenJars() }
    }


    private suspend fun scansOnlyTheGivenJars() {
        val alpha = jar("alpha", mapOf("notation/auto-jvm/alpha/alpha.yaml" to alphaBody, "other/skip.yaml" to "x"))
        val beta = jar("beta", mapOf("notation/auto-jvm/beta/beta.yaml" to betaBody))

        val alphaMedia = OriginNotationMedia(listOf(alpha.toUri().toURL()))
        val paths = alphaMedia.scan().documents.map.keys.map { it.asString() }
        assertEquals(listOf("auto-jvm/alpha/alpha.yaml"), paths)
        assertEquals(alphaBody, alphaMedia.readDocument(DocumentPath.parse("auto-jvm/alpha/alpha.yaml")))
        val origin = alphaMedia.origins().values.single()
        assertTrue(origin.toString().startsWith("jar:file:"), origin.toString())
        assertTrue(origin.toString().endsWith("alpha.jar!/notation/auto-jvm/alpha/alpha.yaml"), origin.toString())

        // The test classpath carries kzen's own notation; an origin over one jar never sees it
        assertTrue(alphaMedia.scan().documents.map.keys.none { it.asString().startsWith("auto-common/") })

        val both = OriginNotationMedia(listOf(alpha.toUri().toURL(), beta.toUri().toURL()))
        assertEquals(2, both.scan().documents.map.size)
        assertEquals(betaBody, both.readDocument(DocumentPath.parse("auto-jvm/beta/beta.yaml")))
    }


    @Test
    fun aPathShippedTwiceWithinOneOriginIsRefusedByName() {
        val first = jar("first", mapOf("notation/auto-jvm/dup/doc.yaml" to alphaBody))
        val second = jar("second", mapOf("notation/auto-jvm/dup/doc.yaml" to betaBody))
        val failure = assertFailsWith<IllegalArgumentException> {
            OriginNotationMedia(listOf(first.toUri().toURL(), second.toUri().toURL()))
        }
        assertTrue(failure.message!!.contains("auto-jvm/dup/doc.yaml"), failure.message)
        assertTrue(failure.message!!.contains("first.jar") && failure.message!!.contains("second.jar"), failure.message)
    }


    private fun jar(name: String, entries: Map<String, String>): Path {
        val path = Files.createTempDirectory("origin").resolve("$name.jar")
        JarOutputStream(Files.newOutputStream(path)).use { out ->
            for ((entry, body) in entries) {
                out.putNextEntry(JarEntry(entry))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return path
    }
}
