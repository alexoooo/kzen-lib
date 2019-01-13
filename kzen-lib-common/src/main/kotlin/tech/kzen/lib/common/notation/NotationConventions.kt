package tech.kzen.lib.common.notation

import tech.kzen.lib.common.api.model.*


object NotationConventions {
    const val isAttribute = "is"

    val isSegment = AttributeSegment.ofKey(isAttribute)

    val isPath       = AttributeNesting.ofAttribute(AttributeName(isAttribute))
    val classPath    = AttributeNesting.ofAttribute(AttributeName("class"))
    val byPath       = AttributeNesting.ofAttribute(AttributeName("by"))
    val usingPath    = AttributeNesting.ofAttribute(AttributeName("using"))
    val ofPath       = AttributeNesting.ofAttribute(AttributeName("of"))
    val metadataPath = AttributeNesting.ofAttribute(AttributeName("meta"))
    val definerPath  = AttributeNesting.ofAttribute(AttributeName("definer"))
    val abstractPath  = AttributeNesting.ofAttribute(AttributeName("abstract"))


    const val prefix: String = "notation/"
    const val suffix: String = ".yaml"

    val kzenBasePath = BundlePath.parse("base/kzen-base.yaml")
    val mainPath = BundlePath.parse("main/main.yaml")
}