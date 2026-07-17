package tech.kzen.lib.common.exec.logic.run.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer


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
//
// SER4: kotlinx wire codec. `epoch`/`structureVersion` ride LongAsStringSerializer (unbounded Longs that JS
// cannot round-trip through a JSON number). `active` is nullable WITHOUT a default, so stock Json encodes it
// as an explicit JSON null when there is no run — this replaces the former literal "null" STRING sentinel
// (do not re-add a default, or an absent-run status would omit the key instead of nulling it).
@Serializable
data class LogicStatus(
    @Serializable(with = LongAsStringSerializer::class)
    val epoch: Long,
    @Serializable(with = LongAsStringSerializer::class)
    val structureVersion: Long,
    val active: LogicRunInfo?
)
