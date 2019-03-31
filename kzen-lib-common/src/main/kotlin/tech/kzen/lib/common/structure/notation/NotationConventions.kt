package tech.kzen.lib.common.structure.notation

import tech.kzen.lib.common.api.model.*
import tech.kzen.lib.common.structure.notation.format.YamlUtils


object NotationConventions {
    const val isKey = "is"
    val isAttributeSegment = AttributeSegment.ofKey(isKey)
    val isAttributeName = AttributeName(isKey)
    val isAttributePath = AttributePath.ofAttribute(isAttributeName)

    val classAttributeName = AttributeName("class")
    val classAttributePath = AttributePath.ofAttribute(classAttributeName)

    val ofKey = "of"
    val ofAttributeSegment = AttributeSegment.ofKey(ofKey)

    val metaAttributeName = AttributeName("meta")
    val metaAttributePath = AttributePath.ofAttribute(metaAttributeName)

//    val definerKey = "definer"
    val definerKey = "by"
    val definerAttributeSegment = AttributeSegment.ofKey(definerKey)
    val definerAttributeName = AttributeName(definerKey)
    val definerAttributePath = AttributePath.ofAttribute(definerAttributeName)

    val creatorKey = "creator"
    val creatorAttributeSegment = AttributeSegment.ofKey(creatorKey)
    val creatorAttributeName = AttributeName(creatorKey)
    val creatorAttributePath = AttributePath.ofAttribute(creatorAttributeName)

    val abstractKey = "abstract"
    val abstractAttributeSegment = AttributeSegment.ofKey(abstractKey)
    val abstractAttributeName = AttributeName(abstractKey)
    val abstractAttributePath = AttributePath.ofAttribute(abstractAttributeName)


    const val documentPathPrefix: String = "notation/"
    const val documentPathSuffix: String = "." + YamlUtils.fileExtension

    val kzenBasePath = DocumentPath.parse("base/kzen-base.yaml")

    val mainKey = "main"
    val mainObjectName = ObjectName(mainKey)
    val mainObjectPath = ObjectPath(mainObjectName, DocumentNesting.root)
    val mainDocumentName = DocumentName.ofFilenameWithDefaultExtension(mainKey)
    val mainDocumentPathSegment = DocumentPathSegment(mainKey)
    val mainDocumentPath = DocumentPath(listOf(mainDocumentPathSegment), null)


    val specialAttributeNames = setOf(
            abstractAttributeName,
            isAttributeName,
            classAttributeName,
            definerAttributeName,
            creatorAttributeName,
            metaAttributeName)

    fun isSpecial(attributeName: AttributeName): Boolean {
        return attributeName in specialAttributeNames
    }
}