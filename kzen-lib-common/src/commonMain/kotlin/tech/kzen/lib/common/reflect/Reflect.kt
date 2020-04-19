package tech.kzen.lib.common.reflect


annotation class Reflect {
    companion object {
        val simpleName: String = Reflect::class.simpleName!!
        val qualifiedName = "tech.kzen.lib.common.reflect.$simpleName"
    }
}