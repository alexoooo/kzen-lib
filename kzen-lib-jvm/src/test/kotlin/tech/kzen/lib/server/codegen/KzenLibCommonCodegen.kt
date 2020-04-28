package tech.kzen.lib.server.codegen

import tech.kzen.lib.platform.ClassName
import java.nio.file.Paths


object KzenLibCommonCodegen {
    @JvmStatic
    fun main(args: Array<String>) {
        ModuleReflectionGenerator.generate(
            Paths.get("kzen-lib-common/src/commonMain/kotlin"),
            ClassName("tech.kzen.lib.common.codegen.KzenLibCommonModule"))
    }
}