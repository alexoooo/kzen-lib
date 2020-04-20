package tech.kzen.lib.server.codegen

import tech.kzen.lib.platform.ClassName
import java.nio.file.Paths


fun main() {
    ModuleReflectionGenerator.generate(
            Paths.get("kzen-lib-common/src/commonMain/kotlin"),
            ClassName("tech.kzen.lib.common.codegen.KzenLibCommonModule"))
}