// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ModuleReflectionGenerator at 2023-11-10T21:59:52.501137600
package tech.kzen.lib.common.codegen

import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.objects.base.AttributeObjectCreator
import tech.kzen.lib.common.objects.base.AttributeObjectDefiner
import tech.kzen.lib.common.objects.base.DefinitionAttributeCreator
import tech.kzen.lib.common.objects.base.StructuralAttributeDefiner
import tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectCreator
import tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectDefiner
import tech.kzen.lib.common.objects.general.AutowiredAttributeDefiner
import tech.kzen.lib.common.objects.general.ParentChildAttributeDefiner
import tech.kzen.lib.common.objects.general.SelfAttributeDefiner
import tech.kzen.lib.common.objects.general.WeakAttributeDefiner


@Suppress("UNCHECKED_CAST")
object KzenLibCommonModule: ModuleReflection {
    override fun register(reflectionRegistry: ReflectionRegistry) {
reflectionRegistry.put(
    "tech.kzen.lib.common.objects.base.AttributeObjectCreator",
    listOf()
) {
    AttributeObjectCreator
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.base.AttributeObjectDefiner",
    listOf()
) {
    AttributeObjectDefiner
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.base.DefinitionAttributeCreator",
    listOf()
) {
    DefinitionAttributeCreator
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.base.StructuralAttributeDefiner",
    listOf()
) {
    StructuralAttributeDefiner
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectCreator",
    listOf()
) {
    DefaultConstructorObjectCreator
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.bootstrap.DefaultConstructorObjectDefiner",
    listOf()
) {
    DefaultConstructorObjectDefiner
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.general.AutowiredAttributeDefiner",
    listOf("weak")
) { args ->
    AutowiredAttributeDefiner(args[0] as Boolean)
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.general.ParentChildAttributeDefiner",
    listOf()
) {
    ParentChildAttributeDefiner
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.general.SelfAttributeDefiner",
    listOf()
) {
    SelfAttributeDefiner
}

reflectionRegistry.put(
    "tech.kzen.lib.common.objects.general.WeakAttributeDefiner",
    listOf()
) {
    WeakAttributeDefiner
}
    }
}
