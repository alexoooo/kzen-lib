package tech.kzen.lib.common.exec.engine.resource


/**
 * One holder's hold on a [Resource] (or on every member of a [ResourceOwners]). While any hold is active the
 * resource stays open; the last release across all holders closes it exactly once. [release] is idempotent:
 * a second call does nothing. A hold on nothing is [none], never active.
 */
interface Hold {
    /** Whether this hold still counts; false after [release]. */
    val isActive: Boolean

    /** Lets go once; a second call does nothing. A close failure on the last release propagates from here. */
    fun release()


    companion object {
        val none: Hold = object: Hold {
            override val isActive: Boolean
                get() = false

            override fun release() {}
        }


        /**
         * Releases every hold (nulls skipped), attempting all of them: the first close failure is thrown after the
         * rest, with the others suppressed on it.
         */
        fun releaseAll(holds: Iterable<Hold?>) {
            var failure: Throwable? = null
            for (hold in holds) {
                try {
                    hold?.release()
                }
                catch (e: Exception) {
                    if (failure == null) failure = e else failure.addSuppressed(e)
                }
            }
            failure?.let { throw it }
        }
    }
}
