package tech.kzen.lib.server.codegen


object KzenLibAllCodegen {
    @JvmStatic
    fun main(args: Array<String>) {
        KzenLibCommonCodegen.main(args)
        KzenLibJvmTestCodegen.main(args)
    }
}