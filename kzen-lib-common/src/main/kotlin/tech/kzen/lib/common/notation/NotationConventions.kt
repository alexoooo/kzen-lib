package tech.kzen.lib.common.notation

import tech.kzen.lib.common.api.model.AttributeName
import tech.kzen.lib.common.api.model.AttributeNesting
import tech.kzen.lib.common.api.model.BundlePath
import tech.kzen.lib.common.api.model.MapKeyAttributeSegment


object NotationConventions {
    const val isAttribute = "is"

    val isSegment = MapKeyAttributeSegment(isAttribute)

    val isPath       = AttributeNesting.ofAttribute(AttributeName(isAttribute))
    val classPath    = AttributeNesting.ofAttribute(AttributeName("class"))
    val byPath       = AttributeNesting.ofAttribute(AttributeName("by"))
    val usingPath    = AttributeNesting.ofAttribute(AttributeName("using"))
    val ofPath       = AttributeNesting.ofAttribute(AttributeName("of"))
    val metadataPath = AttributeNesting.ofAttribute(AttributeName("meta"))
    val definerPath  = AttributeNesting.ofAttribute(AttributeName("definer"))
    val abstractPath  = AttributeNesting.ofAttribute(AttributeName("abstract"))


    const val prefix: String = "attributeNotation/"
    const val suffix: String = ".yaml"

    val kzenBasePath = BundlePath.parse("attributeNotation/base/kzen-base.yaml")
    val mainPath = BundlePath.parse("attributeNotation/main/main.yaml")
}