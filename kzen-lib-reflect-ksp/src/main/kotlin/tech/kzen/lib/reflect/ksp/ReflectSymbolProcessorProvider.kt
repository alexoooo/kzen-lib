package tech.kzen.lib.reflect.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider


class ReflectSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val moduleClassName = environment.options[OPTION_MODULE_CLASS_NAME]
            ?: error(
                "Missing required KSP option '$OPTION_MODULE_CLASS_NAME'. " +
                "Configure it via ksp { arg(\"$OPTION_MODULE_CLASS_NAME\", \"<fqn>\") } in the consuming module."
            )

        return ReflectSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            moduleClassName = moduleClassName
        )
    }


    companion object {
        const val OPTION_MODULE_CLASS_NAME = "kzen.reflect.moduleClassName"
    }
}
