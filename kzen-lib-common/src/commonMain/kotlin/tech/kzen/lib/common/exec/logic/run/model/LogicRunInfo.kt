package tech.kzen.lib.common.exec.logic.run.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer


// SER4: kotlinx wire codec. `sequence` rides LongAsStringSerializer (unbounded Long, JS-unsafe as a JSON
// number). `id`/`state` encode via their SER2 STRING serializer / enum name, byte-identical to the old codec.
@Serializable
data class LogicRunInfo(
    val id: LogicRunId,
    val frame: LogicRunFrameInfo,
    val state: LogicRunState,

    // The run's global monotonic trace high-water (RunState.sequence): every emit / log / park /
    // settle advances it. A consumer that has already projected this sequence has nothing new to
    // fetch, so it doubles as the run's cache version.
    @Serializable(with = LongAsStringSerializer::class)
    val sequence: Long
)
