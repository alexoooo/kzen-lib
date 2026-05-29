package tech.kzen.lib.common.exec.task

/**
 * marker
 */
interface TaskRun {
    fun close(error: Boolean)
}