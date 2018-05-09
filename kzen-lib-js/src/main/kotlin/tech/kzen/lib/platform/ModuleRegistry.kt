package tech.kzen.lib.platform

object ModuleRegistry {
    private val mutableModules = mutableListOf<dynamic>()

    val modules: List<dynamic> =
            mutableModules


    init {
        add(js("require('kzen-lib-js.js')"))
//        add(js("require('kzen-lib-common.js')"))
    }


    @Suppress("MemberVisibilityCanBePrivate")
    fun add(module: dynamic) {
        mutableModules.add(module)
    }
}