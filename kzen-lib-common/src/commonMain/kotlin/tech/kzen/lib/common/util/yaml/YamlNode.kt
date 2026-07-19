package tech.kzen.lib.common.util.yaml


//----------------------------------------------------------------------------------------------------------------
sealed class YamlNode {
    companion object {
        fun ofObject(value: Any?): YamlNode {
            return when (value) {
                null ->
                    YamlString("null")

                is String ->
                    YamlString(value)

                // One `is Number` branch, deliberately — not Int/Long/Float/Double listed separately. On
                // Kotlin/JS every number but Long IS a JS `number`, so `is Int` also matches a Float/Double
                // and makes any numeric branch below it dead code there. Identical bodies kept that harmless
                // here, but the same shape was a live JS-only bug in ExecutionValueSerialization
                // .anyToJsonElement. `is Number` has no ordering hazard, and it also lets Byte/Short render
                // on the JVM the way they already did on JS instead of throwing.
                is Number ->
                    YamlString(value.toString())

                is Boolean ->
                    YamlString(value.toString())

                is List<Any?> ->
                    YamlList(value.map { ofObject(it) })

                is Map<*, Any?> ->
                    YamlMap(value.map { it.key as String to ofObject(it.value) }.toMap())

                else ->
                    throw UnsupportedOperationException(
                        "Unsupported YAML value: $value (${value::class.simpleName})")
            }
        }


        fun ofMap(vararg pairs: Pair<String, Any?>): YamlMap {
            return YamlMap(pairs.map { it.first to ofObject(it.second) }.toMap())
        }


        fun ofList(vararg values: Any?): YamlList {
            return YamlList(values.map { ofObject(it) })
        }
    }


    // Own-line `# ...` comments preceding this node in the source, stored without the leading `#` and one
    // following space. Invariant: no element contains `\n`/`\r`. Participates in data-class equality.
    abstract val comments: List<String>


    abstract fun toObject(): Any


    abstract fun withComments(comments: List<String>): YamlNode
}


//-----------------------------------------------------------------------------------------------------------------
data class YamlString(
        val value: String,
        override val comments: List<String> = listOf()
): YamlNode() {
    companion object {
        val empty = YamlString("")
    }

    override fun toObject(): String {
        return value
    }

    override fun withComments(comments: List<String>): YamlString {
        return copy(comments = comments)
    }
}


//-----------------------------------------------------------------------------------------------------------------
sealed class YamlStructure: YamlNode() {
    fun isEmpty(): Boolean {
        return size() == 0
    }

    abstract fun size(): Int
}


data class YamlList(
        val values: List<YamlNode>,
        override val comments: List<String> = listOf()
): YamlStructure() {
    override fun size(): Int {
        return values.size
    }

    override fun toObject(): List<Any> {
        return values.map { it.toObject() }
    }

    override fun withComments(comments: List<String>): YamlList {
        return copy(comments = comments)
    }
}


data class YamlMap(
        val values: Map<String, YamlNode>,
        override val comments: List<String> = listOf()
): YamlStructure() {
    override fun size(): Int {
        return values.size
    }

    override fun toObject(): Map<String, Any> {
        return values.mapValues { it.value.toObject() }
    }

    override fun withComments(comments: List<String>): YamlMap {
        return copy(comments = comments)
    }
}
