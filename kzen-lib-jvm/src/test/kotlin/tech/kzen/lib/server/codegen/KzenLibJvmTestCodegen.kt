package tech.kzen.lib.server.codegen

import tech.kzen.lib.platform.ClassName
import java.nio.file.Paths


fun main() {
    ModuleReflectionGenerator.generate(
            Paths.get("kzen-lib-jvm/src/test/kotlin"),
            ClassName("tech.kzen.lib.server.codegen.KzenLibJvmTestModule"))
}