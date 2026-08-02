package tech.kzen.lib.common.exec.engine.context


/**
 * What one frame's export declaration covers, and therefore which bindings climb past that frame
 * ([tech.kzen.lib.common.exec.engine.Execution.declareExport]).
 *
 * The distinction is load-bearing, not an optimization. A declaration naming one qualified member must not
 * carry its siblings — moving `db:reporting` because someone exported `db:primary` hands a caller a resource
 * nobody offered it — while a declaration that admits a computed qualifier cannot enumerate what it carries
 * and must take the family. One semantic, shared by notation, static analysis and the engine's climb.
 */
sealed interface ExportSelector {
    /**
     * Coverage of a raw registry key. Total on any string, because the plain-string resource API registers
     * keys that need not be well-formed [ContextKey]s, and the export set has always split them at the first
     * delimiter.
     */
    fun covers(rawKey: String): Boolean


    fun covers(key: ContextKey): Boolean {
        return covers(key.asString())
    }


    /** Exactly one member — what a declared qualifier contributes. */
    data class Exact(val key: ContextKey): ExportSelector {
        override fun covers(rawKey: String): Boolean {
            return key.asString() == rawKey
        }
    }


    /** A whole family, the bare key and every qualifier alike — what an unqualified declaration contributes. */
    data class Family(val family: ContextFamily): ExportSelector {
        override fun covers(rawKey: String): Boolean {
            return rawKey.substringBefore(ContextKey.qualifierDelimiter) == family.value
        }
    }


    companion object {
        /**
         * The string form the raw API declares: a qualified string selects exactly that member, a bare family
         * selects the whole family. That is what the plain-string export set already meant, so a declaration
         * parsed here carries exactly what it carried before.
         */
        fun parse(key: String): ExportSelector {
            val contextKey = ContextKey.parse(key)
            return when (contextKey.qualifier) {
                null -> Family(contextKey.family)
                else -> Exact(contextKey)
            }
        }
    }
}
