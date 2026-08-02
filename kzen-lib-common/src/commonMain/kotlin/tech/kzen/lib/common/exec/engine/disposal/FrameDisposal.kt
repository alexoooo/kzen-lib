package tech.kzen.lib.common.exec.engine.disposal

import tech.kzen.lib.common.exec.engine.ClosePolicy


/**
 * A one-shot teardown the engine owns: the closer, plus what happens to it when the frame holding it settles
 * ([policy]).
 *
 * **At-most-once is structural, not something a closer has to defend against.** Supersession, explicit
 * release and frame settle all route through [claim], which hands the closer to exactly one caller and null
 * to every later one — so only the winner ever invokes third-party code. A closer still has to tolerate the
 * external resource being gone already, but the engine never deliberately invokes one twice, and swallowing a
 * second failure after a side effect has happened is no substitute for that.
 *
 * Carries no synchronization of its own: [claim] is engine-internal and is only ever called under the engine
 * lock, which is what makes the transition atomic with the registry mutation beside it. The closer itself is
 * third-party code and always runs OFF that lock.
 */
class FrameDisposal(
    val policy: ClosePolicy,
    closer: () -> Unit
) {
    private var pending: (() -> Unit)? = closer


    /** The closer, exactly once; null once anything else has taken it. */
    fun claim(): (() -> Unit)? {
        val result = pending
        pending = null
        return result
    }
}
