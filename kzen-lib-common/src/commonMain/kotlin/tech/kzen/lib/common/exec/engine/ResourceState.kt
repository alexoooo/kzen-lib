package tech.kzen.lib.common.exec.engine


/**
 * One open resource in a run, as the run view shows it (logic-spec §6): what it is, who holds it and how many
 * holds each has. A hold count that never reaches zero is visible here by holder name, which is what lets a
 * user see why something has not closed.
 */
data class ResourceState(
    /** The native's class name, or `anonymous` for a teardown that stands for no handle. */
    val description: String,

    /** Whether this run may never close it ([tech.kzen.lib.common.exec.engine.resource.Resource.borrowed]). */
    val borrowed: Boolean,

    /** Live holds by holder name. */
    val holds: Map<String, Int>
)
