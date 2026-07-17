package tech.kzen.lib.common.exec.logic.run.model


// A snapshot of the controller's run state, versioned so a consumer can tell "nothing changed" from
// "something changed" without diffing. Deliberately carries NO wall clock: a timestamp stamped per
// call is fresh on every poll, so any consumer keying a cache on it re-fetches forever (which is
// exactly what the retired `time` field caused). Three axes here, each moving only when something
// observable actually moved:
//   - epoch  — controller-scoped, bumped on the transitions `sequence` cannot express: a run starts,
//              a run settles terminal, or a retained trace is cleared. Bumps even when `active` is
//              null, which is what lets a consumer notice a post-run "clear traces".
//   - active.sequence — the run's monotonic trace high-water (see LogicRunInfo). Advances on EVERY
//              engine emit, so a per-emit re-fetch key belongs on it (the values genuinely changed).
//   - structureVersion — controller-scoped, bumped only on a genuine EXECUTION-TREE change: an
//              execution created/destroyed, a run-state transition, or a run lifecycle/clear event
//              (epoch is folded into it, so all epoch bumps also bump this). Explicitly does NOT move
//              on a plain frame-position advance within a stable execution set — so a consumer whose
//              answer changes only on structure (the traced-document set, the execution tree) keys on
//              this and stops re-fetching per emit. Present even when `active` is null, like epoch.
data class LogicStatus(
    val epoch: Long,
    val structureVersion: Long,
    val active: LogicRunInfo?
) {
    companion object {
        private const val epochKey = "epoch"
        private const val structureVersionKey = "structureVersion"
        private const val activeKey = "active"

        fun ofCollection(collection: Map<String, Any>): LogicStatus {
            val active = when (
                val activeValue = collection[activeKey]!!
            ) {
                "null" ->
                    null

                else ->
                    @Suppress("UNCHECKED_CAST")
                    (LogicRunInfo.ofCollection(activeValue as Map<String, Any>))
            }

            return LogicStatus(
                (collection[epochKey] as String).toLong(),
                (collection[structureVersionKey] as String).toLong(),
                active)
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            epochKey to epoch.toString(),
            structureVersionKey to structureVersion.toString(),
            activeKey to (active?.toCollection() ?: "null"))
    }
}
