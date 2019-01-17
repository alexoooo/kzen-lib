package tech.kzen.lib.common.notation

import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributePath
import tech.kzen.lib.common.api.model.AttributeSegment
import tech.kzen.lib.common.api.model.BundlePath


object NotationConventions {
    const val isKey = "is"
    val isSegment = AttributeSegment.ofKey(isKey)
    val isAttribute = AttributePath.ofAttribute(AttributeName(isKey))

    val classAttribute    = AttributePath.ofAttribute(AttributeName("class"))
    val byAttribute       = AttributePath.ofAttribute(AttributeName("by"))
    val usingAttribute    = AttributePath.ofAttribute(AttributeName("using"))
    val ofAttribute       = AttributePath.ofAttribute(AttributeName("of"))
    val metaAttribute     = AttributePath.ofAttribute(AttributeName("meta"))
    val definerAttribute  = AttributePath.ofAttribute(AttributeName("definer"))
    val abstractAttribute = AttributePath.ofAttribute(AttributeName("abstract"))


    const val prefix: String = "notation/"
    const val suffix: String = ".yaml"

    val kzenBasePath = BundlePath.parse("base/kzen-base.yaml")
    val mainPath = BundlePath.parse("main/main.yaml")
}