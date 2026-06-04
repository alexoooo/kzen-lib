package tech.kzen.lib.server.objects.service

import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * A graph object whose `label` comes from notation while `service` is injected at construction from
 * the GraphEnvironment (no notation entry for it).
 */
@Reflect
class ServiceHolder(
    val label: String,
    @Service val service: SampleService
)
