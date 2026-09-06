package tech.kzen.lib.server.reflect

import tech.kzen.lib.platform.ClassName
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue


class AggregateClassLoaderTest {
    private class Recording: AggregateClassLoader.Diagnostics {
        val shadowed = mutableListOf<Pair<String, String>>()
        val ambiguous = mutableListOf<Pair<List<String>, String>>()
        override fun shadowed(scopeId: String, className: String) { shadowed.add(scopeId to className) }
        override fun ambiguous(scopeIds: List<String>, className: String) { ambiguous.add(scopeIds to className) }
    }


    @Test
    fun parentWinsUniqueScopeClassIsServedByItsScopeAndDuplicatesAreAmbiguous() {
        val appJar = jar("app", mapOf("fixture.Shared" to javaClass("fixture", "Shared", "app")))
        val alphaJar = jar("alpha", mapOf(
            "fixture.Shared" to javaClass("fixture", "Shared", "alpha"),
            "fixture.Alpha" to javaClass("fixture", "Alpha", "alpha"),
            "fixture.Twice" to javaClass("fixture", "Twice", "alpha")))
        val betaJar = jar("beta", mapOf("fixture.Twice" to javaClass("fixture", "Twice", "beta")))

        val parent = URLClassLoader(arrayOf(appJar.toUri().toURL()), AggregateClassLoaderTest::class.java.classLoader)
        val alpha = URLClassLoader(arrayOf(alphaJar.toUri().toURL()), parent)
        val beta = URLClassLoader(arrayOf(betaJar.toUri().toURL()), parent)
        val diagnostics = Recording()
        val aggregate = AggregateClassLoader(parent, listOf(
            AggregateClassLoader.Scope("alpha", alpha), AggregateClassLoader.Scope("beta", beta)), diagnostics)

        val shared = aggregate.loadClass("fixture.Shared")
        assertEquals("app", origin(shared))
        assertSame(parent.loadClass("fixture.Shared"), shared)
        assertSame(alpha.loadClass("fixture.Shared"), shared, "the scope's own loader is parent-first too")
        aggregate.loadClass("fixture.Shared")
        assertEquals(listOf("alpha" to "fixture.Shared"), diagnostics.shadowed, "reported once")

        val alphaClass = aggregate.loadClass("fixture.Alpha")
        assertEquals("alpha", origin(alphaClass))
        assertSame(alpha.loadClass("fixture.Alpha"), alphaClass, "the scope's existing Class, never a copy")
        assertSame(alphaClass, Class.forName("fixture.Alpha", false, aggregate))

        val failure = assertFailsWith<AmbiguousClassException> { aggregate.loadClass("fixture.Twice") }
        assertEquals(listOf("alpha", "beta"), failure.definingScopes)
        assertEquals(listOf(listOf("alpha", "beta") to "fixture.Twice"), diagnostics.ambiguous)
        assertFailsWith<ClassNotFoundException> { aggregate.loadClass("fixture.Missing") }

        val mirror = ReflectiveClassMirror(aggregate)
        assertTrue(mirror.contains(ClassName("fixture.Twice")), "ambiguity is served as a named failure")
        val mirrorFailure = assertFailsWith<IllegalArgumentException> {
            mirror.constructorArgumentNames(ClassName("fixture.Twice"))
        }
        assertTrue(mirrorFailure.message!!.contains("2 plugin scopes"), mirrorFailure.message)
    }


    private fun origin(clazz: Class<*>): String {
        return clazz.getMethod("origin").invoke(clazz.getDeclaredConstructor().newInstance()) as String
    }


    private fun javaClass(packageName: String, simpleName: String, origin: String): String {
        return "package $packageName;\n@tech.kzen.lib.common.reflect.Reflect\npublic class $simpleName {\n" +
            "    public String origin() { return \"$origin\"; }\n}\n"
    }


    private fun jar(name: String, sources: Map<String, String>): Path {
        val classes = compile(sources)
        val path = Files.createTempDirectory("aggregate").resolve("$name.jar")
        JarOutputStream(Files.newOutputStream(path)).use { out ->
            for ((className, bytes) in classes) {
                out.putNextEntry(JarEntry(className.replace('.', '/') + ".class"))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return path
    }


    private fun compile(sources: Map<String, String>): Map<String, ByteArray> {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val outputs = mutableMapOf<String, ByteArrayOutputStream>()
        val standard = compiler.getStandardFileManager(null, null, null)
        val fileManager = object: javax.tools.ForwardingJavaFileManager<javax.tools.StandardJavaFileManager>(standard) {
            override fun getJavaFileForOutput(
                location: javax.tools.JavaFileManager.Location, className: String,
                kind: JavaFileObject.Kind, sibling: javax.tools.FileObject?
            ): JavaFileObject {
                val buffer = ByteArrayOutputStream()
                outputs[className] = buffer
                return object: SimpleJavaFileObject(URI.create("bytes:///" + className.replace('.', '/') + kind.extension), kind) {
                    override fun openOutputStream() = buffer
                }
            }
        }
        val units = sources.map { (name, source) ->
            object: SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + ".java"), JavaFileObject.Kind.SOURCE) {
                override fun getCharContent(ignoreEncodingErrors: Boolean) = source
            }
        }
        val options = listOf("-classpath", System.getProperty("java.class.path"), "-proc:none")
        check(compiler.getTask(null, fileManager, null, options, null, units).call()) { "fixture compilation failed" }
        return outputs.mapValues { it.value.toByteArray() }
    }
}
