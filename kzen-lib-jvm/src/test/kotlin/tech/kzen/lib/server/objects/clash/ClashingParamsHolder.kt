package tech.kzen.lib.server.objects.clash

import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.server.objects.clash.alpha.Payload as AlphaPayload
import tech.kzen.lib.server.objects.clash.omega.Payload as OmegaPayload


/**
 * Two constructor parameters whose types share a simple name across packages: the fixture source can
 * alias them, the generated registration can't, so it must render both types fully qualified.
 */
@Reflect
class ClashingParamsHolder(
    val first: AlphaPayload,
    val second: OmegaPayload
)
