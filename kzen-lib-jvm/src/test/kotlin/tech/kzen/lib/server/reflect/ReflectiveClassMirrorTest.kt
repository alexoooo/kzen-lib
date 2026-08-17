package tech.kzen.lib.server.reflect

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.objects.EscapedObject
import tech.kzen.lib.server.objects.StringHolder
import tech.kzen.lib.server.objects.StringHolderNullableRef
import tech.kzen.lib.server.objects.custom.CustomModel
import tech.kzen.lib.server.objects.nested.NestedObject
import tech.kzen.lib.server.objects.nested.user.NestedUser
import tech.kzen.lib.server.objects.reflective.JavaServiceHolder
import tech.kzen.lib.server.objects.service.SampleService
import tech.kzen.lib.server.objects.service.ServiceHolder
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue


/**
 * Pins the reflective fallback against the KSP-generated registrations: every behaviour is asserted
 * as equal to what [tech.kzen.lib.common.reflect.ReflectionRegistry] answers for the same class, so
 * parity holds by construction instead of against hand-maintained expectations.
 */
class ReflectiveClassMirrorTest {
    private val registry = JvmGraphTestUtils.reflectionRegistry
    private val mirror = ReflectiveClassMirror()


    //-----------------------------------------------------------------------------------------------------------------
    private fun className(javaClass: Class<*>): ClassName {
        // The registry name convention is the JVM binary name
        return ClassName(javaClass.name)
    }


    private fun assertArgumentNameParity(javaClass: Class<*>, expected: List<String>) {
        val className = className(javaClass)
        assertTrue(mirror.contains(className))
        assertEquals(expected, registry.constructorArgumentNames(className))
        assertEquals(expected, mirror.constructorArgumentNames(className))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun `constructor argument names follow declaration order`() {
        assertArgumentNameParity(StringHolder::class.java, listOf("value"))

        val instance = mirror.create(className(StringHolder::class.java), listOf("hello")) as StringHolder
        val generated = registry.create(className(StringHolder::class.java), listOf("hello")) as StringHolder

        assertEquals(generated.value, instance.value)
    }


    @Test
    fun `keyword constructor argument name is preserved`() {
        assertArgumentNameParity(EscapedObject::class.java, listOf("else"))
    }


    @Test
    fun `nested classes resolve by their dollar-joined binary name`() {
        assertArgumentNameParity(NestedObject.Nested::class.java, listOf("foo"))
        assertArgumentNameParity(NestedUser.Nested2::class.java, listOf("delegate"))

        val nested = mirror.create(className(NestedObject.Nested::class.java), listOf(42)) as NestedObject.Nested
        assertEquals(42, nested.foo())
    }


    @Test
    fun `kotlin object is served as its singleton`() {
        val className = className(CustomModel.Definer::class.java)

        assertArgumentNameParity(CustomModel.Definer::class.java, listOf())
        assertSame(registry.create(className, listOf()), mirror.create(className, listOf()))
    }


    @Test
    fun `service arguments are keyed by parameter name and dotted qualified type name`() {
        val className = className(ServiceHolder::class.java)

        assertArgumentNameParity(ServiceHolder::class.java, listOf("label", "service"))

        val expected = mapOf("service" to ClassName("tech.kzen.lib.server.objects.service.SampleService"))
        assertEquals(expected, registry.serviceArguments(className))
        assertEquals(expected, mirror.serviceArguments(className))

        val sampleService = SampleService("token")
        val instance = mirror.create(className, listOf("x", sampleService)) as ServiceHolder

        assertEquals("x", instance.label)
        assertSame(sampleService, instance.service)
    }


    @Test
    fun `nullable constructor argument accepts null`() {
        val className = className(StringHolderNullableRef::class.java)

        val instance = mirror.create(className, listOf(null)) as StringHolderNullableRef
        assertEquals(registry.create(className, listOf(null)), instance)
    }


    @Test
    fun `generic constructor argument erases to its runtime value`() {
        val className = className(NestedObject.Nested2::class.java)

        val instance = mirror.create(className, listOf(listOf(11, 22))) as NestedObject.Nested2<*>
        assertEquals(registry.create(className, listOf(listOf(11, 22))), instance)
    }


    @Test
    fun `class without the Reflect annotation is not served`() {
        assertFalse(mirror.contains(className(SampleService::class.java)))
    }


    @Test
    fun `class that cannot be loaded is not served`() {
        assertFalse(mirror.contains(ClassName("tech.kzen.does.not.Exist")))
    }


    @Test
    fun `java class is served with its parameter names and service annotations`() {
        val className = className(JavaServiceHolder::class.java)

        assertTrue(mirror.contains(className))
        assertEquals(listOf("label", "service"), mirror.constructorArgumentNames(className))
        assertEquals(
            mapOf("service" to ClassName("tech.kzen.lib.server.objects.service.SampleService")),
            mirror.serviceArguments(className))

        val sampleService = SampleService("token")
        val instance = mirror.create(className, listOf("hello", sampleService)) as JavaServiceHolder

        assertEquals("hello", instance.label)
        assertSame(sampleService, instance.service)
    }
}
