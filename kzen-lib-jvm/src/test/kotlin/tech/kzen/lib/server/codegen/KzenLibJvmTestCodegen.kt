package tech.kzen.lib.server.codegen

import tech.kzen.lib.platform.ClassName
import java.nio.file.Paths


object KzenLibJvmTestCodegen {
    @JvmStatic
    fun main(args: Array<String>) {
        ModuleReflectionGenerator.generate(
            Paths.get("kzen-lib-jvm/src/test/kotlin"),
            ClassName("tech.kzen.lib.server.codegen.KzenLibJvmTestModule"))
    }
}