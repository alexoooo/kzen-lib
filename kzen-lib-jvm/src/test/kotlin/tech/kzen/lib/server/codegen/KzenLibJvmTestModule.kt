// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2023-11-13T10:07:56.735201900
package tech.kzen.lib.server.codegen

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.server.objects.ast.DivideOperation
import tech.kzen.lib.server.objects.ast.DoubleExpression
import tech.kzen.lib.server.objects.ast.DoubleValue
import tech.kzen.lib.server.objects.ast.PlusOperation
import tech.kzen.lib.server.objects.ast.PlusOperationNamed
import tech.kzen.lib.server.objects.ast.PlusOperationNamedNominal
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.server.objects.autowire.ConcreteObject
import tech.kzen.lib.server.objects.autowire.ObjectGroup
import tech.kzen.lib.server.objects.autowire.ObjectItem
import tech.kzen.lib.server.objects.autowire.StrongHolder
import tech.kzen.lib.server.objects.autowire.WeakHolder
import tech.kzen.lib.server.objects.CommentArgObject
import tech.kzen.lib.server.objects.custom.CustomDefined
import tech.kzen.lib.server.objects.custom.CustomModel
import tech.kzen.lib.server.objects.EscapedObject
import tech.kzen.lib.server.objects.nested.NestedObject
import tech.kzen.lib.server.objects.nested.user.NestedUser
import tech.kzen.lib.server.objects.SelfAware
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.server.objects.StringHolder
import tech.kzen.lib.server.objects.StringHolderNullableNominal
import tech.kzen.lib.server.objects.StringHolderNullableRef
import tech.kzen.lib.server.objects.StringHolderRef


@Suppress("UNCHECKED_CAST")
object KzenLibJvmTestModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
reflectionRegistry.put(
    "tech.kzen.lib.server.objects.ast.DivideOperation",
    listOf("dividend", "divisor")
) { args ->
    DivideOperation(args[0] as DoubleExpression, args[1] as DoubleExpression)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.ast.DoubleValue",
    listOf("value")
) { args ->
    DoubleValue(args[0] as Double)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.ast.PlusOperation",
    listOf("addends")
) { args ->
    PlusOperation(args[0] as List<DoubleExpression>)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.ast.PlusOperationNamed",
    listOf("addends")
) { args ->
    PlusOperationNamed(args[0] as Map<String, DoubleExpression>)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.ast.PlusOperationNamedNominal",
    listOf("addends")
) { args ->
    PlusOperationNamedNominal(args[0] as Map<String, ObjectLocation>)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.autowire.ConcreteObject",
    listOf()
) {
    ConcreteObject()
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.autowire.ObjectGroup",
    listOf("children")
) { args ->
    ObjectGroup(args[0] as List<ObjectItem>)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.autowire.ObjectItem",
    listOf()
) {
    ObjectItem()
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.autowire.StrongHolder",
    listOf("concreteObjects")
) { args ->
    StrongHolder(args[0] as List<ConcreteObject>)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.autowire.WeakHolder",
    listOf("locations")
) { args ->
    WeakHolder(args[0] as List<ObjectLocation>)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.CommentArgObject",
    listOf("first", "fourth")
) { args ->
    CommentArgObject(args[0] as String, args[1] as String)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.custom.CustomDefined",
    listOf("customModel")
) { args ->
    CustomDefined(args[0] as CustomModel)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.custom.CustomModel\$Definer",
    listOf()
) {
    CustomModel.Definer
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.EscapedObject",
    listOf("else")
) { args ->
    EscapedObject(args[0] as String)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.nested.NestedObject\$Nested",
    listOf("foo")
) { args ->
    NestedObject.Nested(args[0] as Int)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.nested.NestedObject\$Nested2",
    listOf("foo")
) { args ->
    NestedObject.Nested2(args[0] as List<Any>)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.nested.user.NestedUser\$Nested",
    listOf("objectLocation", "delegate")
) { args ->
    NestedUser.Nested(args[0] as ObjectLocation, args[1] as NestedObject.Nested)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.nested.user.NestedUser\$Nested2",
    listOf("delegate")
) { args ->
    NestedUser.Nested2(args[0] as NestedUser.Nested)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.SelfAware",
    listOf("objectLocation", "objectNotation", "documentNotation")
) { args ->
    SelfAware(args[0] as ObjectLocation, args[1] as ObjectNotation, args[2] as DocumentNotation)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.StringHolder",
    listOf("value")
) { args ->
    StringHolder(args[0] as String)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.StringHolderNullableNominal",
    listOf("stringHolderOrNull")
) { args ->
    StringHolderNullableNominal(args[0] as ObjectLocation?)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.StringHolderNullableRef",
    listOf("stringHolderOrNull")
) { args ->
    StringHolderNullableRef(args[0] as StringHolder?)
}

reflectionRegistry.put(
    "tech.kzen.lib.server.objects.StringHolderRef",
    listOf("stringHolder")
) { args ->
    StringHolderRef(args[0] as StringHolder)
}
    }
}
