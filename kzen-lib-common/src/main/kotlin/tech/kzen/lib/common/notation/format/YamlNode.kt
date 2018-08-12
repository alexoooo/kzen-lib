package tech.kzen.lib.common.notation.format


//-----------------------------------------------------------------------------------------------------------------
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
abstract class YamlScalar : YamlNode() {
    abstract val value: Any?

    override fun asString(): String {
        return value.toString()
    }
}


// TODO: add |- multi-line support
data class YamlString(
        override val value: String
) : YamlScalar() {
    companion object {
        // TODO: consolidate with parser
        private val bareString = Regex("[0-9a-zA-Z_-]+")

        val empty = YamlString("")
    }

    override fun asString(): String {
        return if (value.matches(bareString)) {
            value
        }
        else {
            // TODO: JSON encoding, and quotation type
            "\"$value\""
        }
    }
}


data class YamlDouble(
        override val value: Double
) : YamlScalar()


data class YamlLong(
        override val value: Long
) : YamlScalar()


abstract class YamlBoolean(
        override val value: Boolean
) : YamlScalar()

object YamlTrue : YamlBoolean(true)
object YamlFalse : YamlBoolean(false)



object YamlNull : YamlScalar() {
    override val value: Any? = null
}


data class YamlList(
        val values: List<YamlNode>
) : YamlNode() {
    override fun asString(): String {
        return values.map {
            val lines = it.asString().split("\n")

            val buffer = mutableListOf<String>()

            buffer.add("- ${lines[0]}")

            for (i in 1 until lines.size) {
                buffer.add("  ${lines[i]}")
            }

            buffer.joinToString("\n")
        }.joinToString("\n")
    }
}


data class YamlMap(
        val values: Map<String, YamlNode>
) : YamlNode() {
    override fun asString(): String {
        return values.map {
            val lines = it.value.asString().split("\n")

            val keyPrefix = YamlString(it.key).asString()

            if (lines.size == 1) {
                "$keyPrefix: ${lines[0]}"
            }
            else {
                "$keyPrefix:\n" + lines.map { "  $it" }.joinToString("\n")
            }
        }.joinToString("\n")
    }
}

