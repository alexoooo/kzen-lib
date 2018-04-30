package tech.kzen.lib.platform


actual object Mirror {
    actual fun contains(className: String): Boolean {
        TODO()
    }

    actual fun constructorArgumentNames(className: String): List<String> {
        TODO()
    }

//    actual fun singletonClassNames(): List<String> {
//        TODO()
//    }

    actual fun create(className: String, constructorArguments: List<Any?>): Any {
        TODO()
    }
}