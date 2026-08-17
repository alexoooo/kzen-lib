package tech.kzen.lib.common.service.context.environment

import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.objects.service.SampleService
import tech.kzen.lib.server.objects.service.ServiceHolder
import tech.kzen.lib.server.util.JvmGraphTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame


class ServiceInjectionTest {
    @Test
    fun `@Service constructor parameter is injected from the GraphEnvironment`() {
        val graphNotation = JvmGraphTestUtils.readNotation()
        val graphMetadata = JvmGraphTestUtils.graphMetadata(graphNotation)
        val graphStructure = GraphStructure(graphNotation, graphMetadata)
        val graphDefinition = GraphDefiner.tryDefine(graphStructure).transitiveSuccessful

        val sampleService = SampleService("injected-token")
        val environment = GraphEnvironment.builder()
            .put(ClassName(SampleService::class.qualifiedName!!), sampleService)
            .build()

        val graphInstance = GraphCreator.createGraph(graphDefinition, environment)

        val location = ObjectLocation(
            DocumentPath.parse("test/service-test.yaml"),
            ObjectPath.parse("ServiceHolder"))
        val instance = graphInstance[location]?.reference as ServiceHolder

        assertEquals("hello", instance.label)
        assertSame(sampleService, instance.service)
    }


    @Test
    fun `provider-registered service is lazy and memoized across createGraph calls`() {
        val graphNotation = JvmGraphTestUtils.readNotation()
        val graphMetadata = JvmGraphTestUtils.graphMetadata(graphNotation)
        val graphStructure = GraphStructure(graphNotation, graphMetadata)
        val graphDefinition = GraphDefiner.tryDefine(graphStructure).transitiveSuccessful

        var invocations = 0
        val environment = GraphEnvironment.builder()
            .put(ClassName(SampleService::class.qualifiedName!!)) {
                invocations++
                SampleService("provided-token")
            }
            .build()

        assertEquals(0, invocations)

        val first = GraphCreator.createGraph(graphDefinition, environment)
        val second = GraphCreator.createGraph(graphDefinition, environment)

        assertEquals(1, invocations)

        val location = ObjectLocation(
            DocumentPath.parse("test/service-test.yaml"),
            ObjectPath.parse("ServiceHolder"))

        val firstService = (first[location]?.reference as ServiceHolder).service
        val secondService = (second[location]?.reference as ServiceHolder).service

        assertEquals("provided-token", firstService.token)
        assertSame(firstService, secondService)
    }
}
