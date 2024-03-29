package tech.kzen.lib.common.service.notation

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentName
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.document.DocumentSegment
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.util.yaml.YamlParser
import tech.kzen.lib.platform.collect.persistentListOf


object NotationConventions {
    const val mainKey = "main"

    const val pathDelimiter = "/"
    const val notationFormat = YamlParser.fileExtension

    const val documentPathPrefix: String = "notation/"

    const val fileDocumentSuffix: String = ".$notationFormat"

    const val directoryDocumentName = "~$mainKey$fileDocumentSuffix"
    const val directoryDocumentSuffix = "$pathDelimiter$directoryDocumentName"

    const val isKey = "is"
    val isAttributeSegment = AttributeSegment.ofKey(isKey)
    val isAttributeName = AttributeName(isKey)
    val isAttributePath = AttributePath.ofName(isAttributeName)

    val classAttributeName = AttributeName("class")
    val classAttributePath = AttributePath.ofName(classAttributeName)
    val classAttributeSegment = AttributeSegment.ofKey(classAttributeName.value)

    const val ofKey = "of"
    val ofAttributeSegment = AttributeSegment.ofKey(ofKey)

    // todo: rename to "info"?
    val metaAttributeName = AttributeName("meta")
    val metaAttributePath = AttributePath.ofName(metaAttributeName)

    const val definerKey = "by"
    val definerAttributeSegment = AttributeSegment.ofKey(definerKey)
    val definerAttributeName = AttributeName(definerKey)
    val definerAttributePath = AttributePath.ofName(definerAttributeName)

    const val creatorKey = "creator"
    val creatorAttributeSegment = AttributeSegment.ofKey(creatorKey)
    val creatorAttributeName = AttributeName(creatorKey)
    val creatorAttributePath = AttributePath.ofName(creatorAttributeName)

    const val nullableKey = "nullable"
    val nullableAttributeSegment = AttributeSegment.ofKey(nullableKey)

//    const val initializerKey = "init"
//    val initializerAttributeSegment = AttributeSegment.ofKey(initializerKey)
//    val initializerAttributeName = AttributeName(initializerKey)
//    val initializerAttributePath = AttributePath.ofName(initializerAttributeName)

    const val abstractKey = "abstract"
    val abstractAttributeSegment = AttributeSegment.ofKey(abstractKey)
    val abstractAttributeName = AttributeName(abstractKey)
    val abstractAttributePath = AttributePath.ofName(abstractAttributeName)


    const val refValue = "ref"
    val refAttributeSegment = AttributeSegment.ofKey(refValue)
    val refAttributePath = AttributePath(metaAttributeName, AttributeNesting(persistentListOf(refAttributeSegment)))

    val kzenBaseDocumentNesting = DocumentNesting.parse("base/")
    val kzenBasePath = DocumentPath(DocumentName("kzen-base"), kzenBaseDocumentNesting, false)

    val mainObjectName = ObjectName(mainKey)
    val mainObjectPath = ObjectPath(mainObjectName, ObjectNesting.root)
    val mainDocumentName = DocumentName(mainKey)
    val mainDocumentSegment = DocumentSegment(mainKey)
    val mainDocumentNesting = DocumentNesting(persistentListOf(mainDocumentSegment))


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