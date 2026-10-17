package tech.kzen.lib.common.exec.engine.disposal

import tech.kzen.lib.common.exec.engine.ClosePolicy


/**
 * The teardown a managed binding asks for: the [closer], plus what happens to the binding's hold when the frame
 * holding it settles ([policy]). A request, not the one-shot itself — the engine adopts the bound value as a
 * [tech.kzen.lib.common.exec.engine.resource.Resource] closed by [closer] and takes a frame hold on it, and the
 * resource's own claim is what makes the close at-most-once across supersession, explicit release and settle.
 * A closer still has to tolerate the external resource being gone already, but the engine never deliberately
 * invokes one twice. The closer is third-party code and always runs OFF the engine lock.
 */
class FrameDisposal(
    val policy: ClosePolicy,
    val closer: () -> Unit
)
