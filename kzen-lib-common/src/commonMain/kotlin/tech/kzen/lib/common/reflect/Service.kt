package tech.kzen.lib.common.reflect


/**
 * Marks a primary-constructor parameter of a [Reflect] object as a runtime service, to be supplied
 * by the host's [tech.kzen.lib.common.service.context.environment.GraphEnvironment] at graph-creation time
 * rather than resolved from notation. The KSP reflect processor records which parameters carry this
 * annotation (and their declared type), so the definition layer routes them to the
 * [tech.kzen.lib.common.objects.base.ServiceAttributeCreator].
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Service
