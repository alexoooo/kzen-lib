package tech.kzen.lib.server.codegen

import tech.kzen.lib.platform.ClassName
import java.nio.file.Paths


object KzenLibJvmTestCodegen {
    @JvmStatic
    fun main(args: Array<String>) {
//        ModuleReflectionGenerator.generate(
//            ModuleEntry(
//                Paths.get("C:/Users/ao/IdeaProjects/kzen-auto/kzen-auto-jvm/src/main/kotlin"),
//                ClassName("tech.kzen.auto.client.codegen.KzenAutoJvmModule")),
//            Paths.get("C:/Users/ao/IdeaProjects/kzen-auto/kzen-auto-common/src/commonMain/kotlin"))

        ModuleReflectionGenerator.generate(
            Paths.get("kzen-lib-jvm/src/test/kotlin"),
            ClassName("tech.kzen.lib.server.codegen.KzenLibJvmTestModule"))
    }
}