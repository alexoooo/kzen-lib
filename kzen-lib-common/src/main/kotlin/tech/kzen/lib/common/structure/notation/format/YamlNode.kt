package tech.kzen.lib.common.structure.notation.format


//----------------------------------------------------------------------------------------------------------------
sealed class YamlNode {
    companion object {
        fun ofObject(value: Any?): YamlNode {
            return when (value) {
                null ->
                    YamlNull

                is String ->
                    YamlString(value)

                is Int ->
                    YamlLong(value.toLong())

                is Long ->
                    YamlLong(value)

                is Float ->
                    YamlDouble(value.toDouble())

                is Double ->
                    YamlDouble(value)

                is Boolean ->
                    if (value) YamlTrue else YamlFalse

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


    fun toObject(): Any? {
        return when (this) {
            is YamlScalar ->
                value

            is YamlList ->
                values

            is YamlMap ->
                values
        }
    }


    abstract fun asString(): String
}


//-----------------------------------------------------------------------------------------------------------------
sealed class YamlScalar: YamlNode() {
    abstract val value: Any?

    override fun asString(): String {
        return value.toString()
    }
}


// TODO: add |- multi-line support
data class YamlString(
        override val value: String
): YamlScalar() {
    companion object {
        val empty = YamlString("")
    }

    override fun asString(): String {
        return YamlUtils.deparseString(value)
    }
}


data class YamlDouble(
        override val value: Double
): YamlScalar()


data class YamlLong(
        override val value: Long
): YamlScalar()


abstract class YamlBoolean(
        override val value: Boolean
): YamlScalar()

object YamlTrue: YamlBoolean(true)
object YamlFalse: YamlBoolean(false)



object YamlNull: YamlScalar() {
    override val value: Any? = null
}


//-----------------------------------------------------------------------------------------------------------------
sealed class YamlStructure: YamlNode() {
//    abstract fun collection(): Any

    fun isEmpty(): Boolean {
        return size() == 0
    }

    abstract fun size(): Int
}


data class YamlList(
        val values: List<YamlNode>
): YamlStructure() {
//    override fun collection(): Any {
//        return values
//    }

    override fun size(): Int {
        return values.size
    }


    override fun asString(): String {
        if (values.isEmpty()) {
            return YamlUtils.emptyListJson
        }

        return values.joinToString("\n") {
            val lines = it.asString().split("\n")

            val buffer = mutableListOf<String>()

            buffer.add("- ${lines[0]}")

            for (i in 1 until lines.size) {
                buffer.add("  ${lines[i]}")
            }

            buffer.joinToString("\n")
        }
    }
}


data class YamlMap(
        val values: Map<String, YamlNode>
): YamlStructure() {
//    override fun collection(): Any {
//        return values
//    }


    override fun size(): Int {
        return values.size
    }


    override fun asString(): String {
        if (values.isEmpty()) {
            return YamlUtils.emptyMapJson
        }

        return values.map { entry ->
            val lines = entry.value.asString().split("\n")

            val keyPrefix = YamlString(entry.key).asString()

            if (entry.value is YamlScalar ||
                    (entry.value as YamlStructure).isEmpty()) {
                "$keyPrefix: ${lines[0]}" +
                        lines.subList(1, lines.size).joinToString("") { "\n   $it" }
            }
            else {
                "$keyPrefix:\n" + lines.joinToString("\n") { "  $it" }
            }
        }.joinToString("\n")
    }
}

