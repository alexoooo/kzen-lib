package tech.kzen.lib.common.exec.engine.context


/**
 * The un-qualified part of a [ContextKey]: the namespace a set of related bindings shares — one `browser`, one
 * `sut` per name, one per database.
 *
 * A distinct type from [ContextKey] deliberately. Every family-granular operation
 * ([tech.kzen.lib.common.exec.engine.Execution.hasBindingInFamily], [ExportSelector.Family]) takes only this,
 * so handing it a fully-qualified key stops compiling rather than silently degrading to an exact-key check —
 * which is what a family gate typed as a plain `String` did.
 */
data class ContextFamily(
    val value: String
) {
    init {
        require(value.isNotEmpty()) {
            "Context family must not be empty"
        }
        require(ContextKey.qualifierDelimiter !in value) {
            "Context family must not contain '${ContextKey.qualifierDelimiter}': $value"
        }
    }


    fun asString(): String {
        return value
    }


    override fun toString(): String {
        return value
    }
}
