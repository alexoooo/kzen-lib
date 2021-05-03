package tech.kzen.lib.common.model.locate
//
//import tech.kzen.lib.common.model.attribute.AttributeName
//
//
//data class AttributeTypedLocation(
//    val objectLocation: ObjectLocation,
//    val attribute: AttributeName,
//    val nestingSegments: List<AttributeTypedSegment>
//) {
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        fun parse(asString: String): AttributeTypedLocation {
//            // \d+ -> list index
//            // `+\d+ -> escaped map key (drop first backtick (for literal "`0"))
//            // else -> map key
//            TODO()
//        }
//    }
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun asString(): String {
//        TODO()
////        return objectLocation.asString() +
////                ObjectReference.nestingSeparator +
////                objectPath.asString()
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
////    override fun toString(): String {
////        return asString()
////    }
//}