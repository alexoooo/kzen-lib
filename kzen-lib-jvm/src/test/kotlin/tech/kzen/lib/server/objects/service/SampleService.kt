package tech.kzen.lib.server.objects.service


/**
 * A plain runtime service (not graph-instantiated) supplied via the GraphEnvironment, to exercise
 * @Service construction-time injection.
 */
class SampleService(
    val token: String
)
