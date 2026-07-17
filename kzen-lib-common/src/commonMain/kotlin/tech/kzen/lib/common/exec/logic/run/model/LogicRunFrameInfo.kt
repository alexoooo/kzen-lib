package tech.kzen.lib.common.exec.logic.run.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.kzen.lib.common.model.location.ObjectLocation


// SER4: kotlinx wire codec — a recursive tree (`dependencies` holds more of this type; the KMP-commonMain
// recursive @Serializable was de-risked by Ser4SpikeTest). @SerialName preserves the short wire keys, and
// `position`'s `= null` default keeps a null position OMITTED (stock Json, encodeDefaults=false), matching
// the old buildMap that only put the key when non-null. `location`/`execution` encode via ObjectLocation /
// LogicExecutionId's SER2 STRING serializers, byte-identical to the old asString()/value form.
@Serializable
data class LogicRunFrameInfo(
    @SerialName("location")
    val objectLocation: ObjectLocation,
    @SerialName("execution")
    val executionId: LogicExecutionId,
//    var state: LogicRunFrameState,
    val dependencies: List<LogicRunFrameInfo>,

    // The frame's current position — the last named boundary its node reached (the element about to run,
    // e.g. the Script next-step highlight), resolved server-side from the node's stable id to the current
    // ObjectLocation; null before the first named boundary (or for a node whose position is itself).
    val position: ObjectLocation? = null,
)
