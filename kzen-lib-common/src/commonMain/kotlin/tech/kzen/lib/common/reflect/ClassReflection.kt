package tech.kzen.lib.common.reflect

import tech.kzen.lib.platform.ClassName


data class ClassReflection(
        val constructorArgumentNames: List<String>,

        /**
         * Constructor parameters annotated [Service], mapped to their declared type. The definition
         * layer routes these to the [tech.kzen.lib.common.objects.base.ServiceAttributeCreator],
         * which resolves them from the [tech.kzen.lib.common.service.context.environment.GraphEnvironment].
         */
        val serviceArguments: Map<String, ClassName>,

        val constructorFunction: (List<Any?>) -> Any
)
