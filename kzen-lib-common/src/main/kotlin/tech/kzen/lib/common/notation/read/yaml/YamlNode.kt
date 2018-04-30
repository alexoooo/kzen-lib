package tech.kzen.lib.common.notation.read.yaml


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
                    YamlList(value.map{ofObject(it)})

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
}


//-----------------------------------------------------------------------------------------------------------------
abstract class YamlScalar : YamlNode() {
    abstract val value: Any?
}


data class YamlString(
        override val value: String
) : YamlScalar()


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
) : YamlNode()


data class YamlMap(
        val values: Map<String, YamlNode>
) : YamlNode()

