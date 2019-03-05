package tech.kzen.lib.common.structure.notation

import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.AttributeSegment
import tech.kzen.lib.common.api.model.BundlePath


object NotationConventions {
    const val isKey = "is"
    val isSegment = AttributeSegment.ofKey(isKey)
    val isName = AttributeName(isKey)
    val isPath = AttributePath.ofAttribute(isName)

    val classAttribute    = AttributePath.ofAttribute(AttributeName("class"))
//    val byAttribute       = AttributePath.ofAttribute(AttributeName("by"))
//    val usingAttribute    = AttributePath.ofAttribute(AttributeName("using"))

    val ofKey = "of"
    val ofSegment = AttributeSegment.ofKey(ofKey)
    val ofName = AttributeName(ofKey)
    val ofPath = AttributePath.ofAttribute(ofName)

    val metaAttribute = AttributePath.ofAttribute(AttributeName("meta"))

    val definerKey = "definer"
    val definerSegment = AttributeSegment.ofKey(definerKey)
    val definerName = AttributeName(definerKey)
    val definerPath = AttributePath.ofAttribute(definerName)

    val creatorKey = "creator"
    val creatorSegment = AttributeSegment.ofKey(creatorKey)
    val creatorName = AttributeName(creatorKey)
    val creatorPath = AttributePath.ofAttribute(creatorName)

    val abstractKey = "abstract"
    val abstractSegment = AttributeSegment.ofKey(abstractKey)
    val abstractName = AttributeName(abstractKey)
    val abstractPath = AttributePath.ofAttribute(abstractName)


    const val prefix: String = "notation/"
    const val suffix: String = ".yaml"

    val kzenBasePath = BundlePath.parse("base/kzen-base.yaml")
    val mainPrefix = BundlePath.parse("main")
}