package tech.kzen.lib.common.exec.engine


/**
 * The three step modes (logic-spec §4). The engine computes all three from the execution tree's depth it
 * already owns — a flavour adds no stepping code:
 * - [Into] — advance exactly one boundary, descending into a child if the next boundary enters one.
 * - [Over] — run any child entered on this boundary to completion, pausing at the current frame's next
 *   boundary.
 * - [Out] — run the current frame and its descendants to completion, pausing at the caller's next boundary
 *   (at the root, runs the whole logic to its end).
 */
enum class StepMode {
    Into,
    Over,
    Out
}
