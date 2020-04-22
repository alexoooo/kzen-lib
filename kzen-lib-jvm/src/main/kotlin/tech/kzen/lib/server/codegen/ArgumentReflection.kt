package tech.kzen.lib.server.codegen

import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.asTopLevelImport
import tech.kzen.lib.platform.ClassNames.nested
import tech.kzen.lib.platform.ClassNames.nestedInSimple


data class ArgumentReflection(
    val name: String,
    val type: String,
    val typeClasses: Set<ClassName>
) {
    fun externalType(
        containingType: ClassName,
        typeParameters: List<String>
    ): String {
        if (typeClasses.isEmpty() && typeParameters.isEmpty()) {
            return type
        }
        val topLevel = containingType.asTopLevelImport()

        var buffer = type

        for (typeParameter in typeParameters) {
            buffer = buffer.replace("<$typeParameter>", "<Any>")
        }

        for (typeClass in typeClasses) {
            if (! typeClass.get().startsWith(topLevel)) {
                continue
            }

            buffer = Regex("(\\W|^)" + Regex.escape(typeClass.nested()) + "(\\W|$)")
                .replace(buffer) {
                    if (it.groupValues[1] == ".") {
                        it.value
                    }
                    else {
                        it.groupValues[1] + typeClass.nestedInSimple() + it.groupValues[2]
                    }
                }
        }

        return buffer
    }
}