package tech.kzen.lib.platform.client

object ModuleRegistry {
    private val mutableModules = mutableSetOf<dynamic>()

    fun modules(): List<dynamic> {
        return mutableModules.toList()
    }


    init {
//        add(js("require('kzen-lib-js.js')"))
//        add(js("require('kzen-lib-common.js')"))
    }


    @Suppress("MemberVisibilityCanBePrivate")
    fun add(module: dynamic) {
        mutableModules.add(module)
    }
}