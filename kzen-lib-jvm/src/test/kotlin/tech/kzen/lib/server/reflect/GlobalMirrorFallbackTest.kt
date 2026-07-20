package tech.kzen.lib.server.reflect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.reflect.ClassMirror
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.objects.StringHolder
import tech.kzen.lib.server.objects.reflective.JavaServiceHolder
import tech.kzen.lib.server.objects.service.SampleService
import tech.kzen.lib.server.util.JvmGraphTestUtils


/**
 * Covers the fallback as the graph layer sees it, through the [GlobalMirror] chain that
 * [JvmGraphTestUtils] bootstraps.
 */
class GlobalMirrorFallbackTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val stubOnlyClassName = ClassName("tech.kzen.test.StubOnly")
        private const val stubArgumentName = "stubArgument"
        private val stubInstance = Any()

        // The chain is process-global and append-only, so this must claim only names no other test
        // resolves; StringHolder is claimed to prove the registry still wins over a later delegate
        private val stubClassMirror = StubClassMirror(setOf(
            stubOnlyClassName,
            ClassName(StringHolder::class.java.name)))

        init {
            JvmGraphTestUtils.reflectionRegistry
            GlobalMirror.register(stubClassMirror)
        }
    }


    private class StubClassMirror(
        private val claimed: Set<ClassName>
    ): ClassMirror {
        override fun contains(className: ClassName): Boolean =
            className in claimed

        override fun constructorArgumentNames(className: ClassName): List<String> =
            listOf(stubArgumentName)

        override fun serviceArguments(className: ClassName): Map<String, ClassName> =
            mapOf()

        override fun create(className: ClassName, constructorArguments: List<Any?>): Any =
            stubInstance
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `generated registration wins over a later delegate`() {
        val className = ClassName(StringHolder::class.java.name)

        assertEquals(listOf("value"), GlobalMirror.constructorArgumentNames(className))
        assertEquals("hello", (GlobalMirror.create(className, listOf("hello")) as StringHolder).value)
    }


    @Test
    fun `a name only a delegate claims is served by that delegate`() {
        assertEquals(listOf(stubArgumentName), GlobalMirror.constructorArgumentNames(stubOnlyClassName))
        assertSame(stubInstance, GlobalMirror.create(stubOnlyClassName, listOf()))
    }


    @Test
    fun `a class with no generated registration is served reflectively`() {
        val className = ClassName(JavaServiceHolder::class.java.name)

        // KSP registers Kotlin sources only, which is what makes this fixture a genuine miss
        assertFalse(JvmGraphTestUtils.reflectionRegistry.contains(className))

        val sampleService = SampleService("token")
        val instance = GlobalMirror.create(className, listOf("hello", sampleService)) as JavaServiceHolder

        assertEquals("hello", instance.label)
        assertSame(sampleService, instance.service)
    }


    @Test(expected = IllegalArgumentException::class)
    fun `an unregistered and unannotated class still fails fast`() {
        GlobalMirror.create(ClassName(SampleService::class.java.name), listOf("token"))
    }


    @Test
    fun `a reflectively served class is definable and creatable through the graph`() {
        val documentPath = DocumentPath.parse("test/java-service-holder-test.yaml")

        val documentObjects = YamlNotationParser().parseDocumentObjects("""
            JavaServiceHolder:
              class: tech.kzen.lib.server.objects.reflective.JavaServiceHolder
              label: "hello"
              meta:
                label: String
        """.trimIndent())

        val graphNotation = JvmGraphTestUtils
            .readNotation()
            .withNewDocument(documentPath, DocumentNotation(documentObjects, null))

        val graphInstance = JvmGraphTestUtils.newObjectGraph(graphNotation)

        val objectLocation = ObjectLocation(documentPath, ObjectPath.parse("JavaServiceHolder"))
        val instance = graphInstance[objectLocation]?.reference as JavaServiceHolder

        assertEquals("hello", instance.label)
        assertEquals("test-service-token", instance.service.token)
    }
}
