package tech.kzen.lib.common.util.yaml


//----------------------------------------------------------------------------------------------------------------
// TODO: add comment support
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


    abstract fun toObject(): Any
}


//-----------------------------------------------------------------------------------------------------------------
// TODO: add |- multi-line support
data class YamlString(
        val value: String
): YamlNode() {
    companion object {
        val empty = YamlString("")
    }

    override fun toObject(): String {
        return value
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
        val values: List<YamlNode>
): YamlStructure() {
    override fun size(): Int {
        return values.size
    }

    override fun toObject(): List<Any> {
        return values.map { it.toObject() }
    }
}


data class YamlMap(
        val values: Map<String, YamlNode>
): YamlStructure() {
    override fun size(): Int {
        return values.size
    }

    override fun toObject(): Map<String, Any> {
        return values.mapValues { it.value.toObject() }
    }
}

