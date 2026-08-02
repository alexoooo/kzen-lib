package tech.kzen.lib.common.exec.engine.context


/**
 * The result of an ambient-binding read, keeping "nothing is bound under this key" distinct from "a binding
 * holds null".
 *
 * A plain `Any?` return cannot carry that difference, and a Context whose value contract is nullable makes it
 * real: `null` is then a value someone deliberately bound, not the absence of one. Presence is
 * registration-existence, never value-non-nullness.
 */
sealed interface BindingLookup {
    data object Missing: BindingLookup

    data class Present(val value: Any?): BindingLookup


    /** The bound value, with both meanings collapsed onto null — what the lossy plain-string read returns. */
    fun valueOrNull(): Any? {
        return when (this) {
            Missing -> null
            is Present -> value
        }
    }
}
