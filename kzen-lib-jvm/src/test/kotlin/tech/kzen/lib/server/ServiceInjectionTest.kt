package tech.kzen.lib.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.context.environment.GraphEnvironment
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.server.objects.service.SampleService
import tech.kzen.lib.server.objects.service.ServiceHolder
import tech.kzen.lib.server.util.JvmGraphTestUtils


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
}
