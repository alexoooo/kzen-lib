package tech.kzen.lib.common.structure.notation

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.structure.notation.format.YamlUtils


object NotationConventions {
    const val isKey = "is"
    val isAttributeSegment = AttributeSegment.ofKey(isKey)
    val isAttributeName = AttributeName(isKey)
    val isAttributePath = AttributePath.ofName(isAttributeName)

    val classAttributeName = AttributeName("class")
    val classAttributePath = AttributePath.ofName(classAttributeName)

    val ofKey = "of"
    val ofAttributeSegment = AttributeSegment.ofKey(ofKey)

    val metaAttributeName = AttributeName("meta")
    val metaAttributePath = AttributePath.ofName(metaAttributeName)

//    val definerKey = "definer"
    val definerKey = "by"
    val definerAttributeSegment = AttributeSegment.ofKey(definerKey)
    val definerAttributeName = AttributeName(definerKey)
    val definerAttributePath = AttributePath.ofName(definerAttributeName)

    val creatorKey = "creator"
    val creatorAttributeSegment = AttributeSegment.ofKey(creatorKey)
    val creatorAttributeName = AttributeName(creatorKey)
    val creatorAttributePath = AttributePath.ofName(creatorAttributeName)

    val abstractKey = "abstract"
    val abstractAttributeSegment = AttributeSegment.ofKey(abstractKey)
    val abstractAttributeName = AttributeName(abstractKey)
    val abstractAttributePath = AttributePath.ofName(abstractAttributeName)


    const val documentPathPrefix: String = "notation/"
    const val documentPathSuffix: String = "." + YamlUtils.fileExtension

    val kzenBasePath = DocumentPath.parse("base/kzen-base.yaml")

    val mainKey = "main"
    val mainObjectName = ObjectName(mainKey)
    val mainObjectPath = ObjectPath(mainObjectName, ObjectNesting.root)
    val mainDocumentName = DocumentName.ofYaml(mainKey)
    val mainDocumentPathSegment = DocumentSegment(mainKey)
    val mainDocumentPath = DocumentPath(null, DocumentNesting(listOf(mainDocumentPathSegment)))


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