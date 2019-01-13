package tech.kzen.lib.common.api.model


@Suppress("DataClassPrivateConstructor")
data class AttributeSegment private constructor(
        private val asString: String
) {
    companion object {
        fun parse(asString: String): AttributeSegment {
            return ofKey(asString)
        }

        fun ofKey(key: String): AttributeSegment {
            return AttributeSegment(key)
        }

        fun ofIndex(index: Int): AttributeSegment {
            check(index >= 0) { "Must not be negative: $index" }
            return AttributeSegment(index.toString())
        }
    }


    fun asKey(): String {
        return asString
    }

    fun asIndex(): Int? {
        return asString.toIntOrNull()
    }


    fun asString(): String {
        return asString
    }


    override fun toString(): String {
        return asString
    }
}


//data class ListIndexAttributeSegment(
//        val index: Int
//): AttributeSegment() {
//    override fun asString(): String {
//        return index.toString()
//    }
//
//    override fun toString(): String {
//        return asString()
//    }
//}
//
//
//data class MapKeyAttributeSegment(
//        val key: String
//): AttributeSegment() {
//    override fun asString(): String {
//        return key
//    }
//
//    override fun toString(): String {
//        return asString()
//    }
//}