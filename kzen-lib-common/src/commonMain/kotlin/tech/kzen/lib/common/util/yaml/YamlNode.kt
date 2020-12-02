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

                is Int ->
                    YamlString(value.toString())

                is Long ->
                    YamlString(value.toString())

                is Float ->
                    YamlString(value.toString())

                is Double ->
                    YamlString(value.toString())

                is Boolean ->
                    YamlString(value.toString())

                is List<Any?> ->
                    YamlList(value.map { ofObject(it) })

                is Map<*, Any?> ->
                    YamlMap(value.map { it.key as String to ofObject(it.value) }.toMap())

                else ->
                    throw UnsupportedOperationException()
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

