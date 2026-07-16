package tech.kzen.lib.common.exec.logic.run.model


// A snapshot of the controller's run state, versioned so a consumer can tell "nothing changed" from
// "something changed" without diffing. Deliberately carries NO wall clock: a timestamp stamped per
// call is fresh on every poll, so any consumer keying a cache on it re-fetches forever (which is
// exactly what the retired `time` field caused). Both versions here move only when something
// observable actually moved:
//   - epoch  — controller-scoped, bumped on the transitions `sequence` cannot express: a run starts,
//              a run settles terminal, or a retained trace is cleared. Bumps even when `active` is
//              null, which is what lets a consumer notice a post-run "clear traces".
//   - active.sequence — the run's monotonic trace high-water (see LogicRunInfo).
data class LogicStatus(
    val epoch: Long,
    val active: LogicRunInfo?
) {
    companion object {
        private const val epochKey = "epoch"
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
                active)
        }
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            epochKey to epoch.toString(),
            activeKey to (active?.toCollection() ?: "null"))
    }
}
