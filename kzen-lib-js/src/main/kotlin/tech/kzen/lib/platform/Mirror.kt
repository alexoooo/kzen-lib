package tech.kzen.lib.platform


actual object Mirror {
    private val cache: MutableMap<String, Metadata?> = mutableMapOf()


    actual fun contains(className: String): Boolean {
        return reflect(className) != null
    }

    actual fun constructorArgumentNames(className: String): List<String> {
        val metadata = reflect(className)
                ?: throw IllegalArgumentException("Not found: $className")

        return metadata.constructorArgumentNames
    }

//    actual fun singletonClassNames(): List<String> {
//        return emptyList()
//    }

    actual fun create(className: String, constructorArguments: List<Any?>): Any {
        val metadata = reflect(className)
                ?: throw IllegalArgumentException("Not found: $className")

        @Suppress("UnsafeCastFromDynamic")
        return newInstance(metadata.constructorFunction, constructorArguments)
    }


    // https://stackoverflow.com/questions/1606797/use-of-apply-with-new-operator-is-this-possible
    @Suppress("UNUSED_PARAMETER")
    private fun newInstance(constructor: dynamic, args: List<Any?>): dynamic {
//        console.log("constructor", constructor.toString());

        val withPrefix: Array<Any?> = arrayOfNulls(args.size + 1)

        @Suppress("UnsafeCastFromDynamic")
        withPrefix[0] = js("{}")

        for (i in args.indices) {
            withPrefix[i + 1] = args[i]
        }

        return js("new (constructor.bind.apply(constructor, withPrefix))")
    }



    private fun reflect(className: String): Metadata? {
        if (cache.containsKey(className)) {
            return cache[className]
        }

        val reflection = reflectImpl(className)

        cache[className] = reflection

        return reflection
    }


    private fun reflectImpl(className: String): Metadata? {
        val constructorFunction = classForName(className) ?: return null

        val constructorArgumentNames = functionArgumentNames(constructorFunction)
//        println("constructorArgumentNames: $constructorArgumentNames")

        return Metadata(constructorFunction, constructorArgumentNames)
    }


    private fun classForName(className: String): dynamic {
        for (module in ModuleRegistry.modules) {
            return classForNameInModule(className, module)
                    ?: continue
        }
        return null
//        val webInstance = classForNameInModule(className, js("require('kzen-lib-js.js')"))
//        if (webInstance != null) {
////            console.log("found in web", webInstance)
//            return webInstance
//        }

//        val commonInstance = classForNameInModule(className, js("require('kzen-lib-common.js')"))
//        check(commonInstance != null, {"Unable to find: $className"})
//
//        return commonInstance
    }


    private fun classForNameInModule(className: String, module: dynamic): dynamic {
        var next = module

        for (i in className.split('.')) {
//            console.log("classForName - next:", next)
            next = next[i]

            if (next == null) {
                return null
            }
        }

        return js("next")
    }


    // https://stackoverflow.com/questions/1007981/how-to-get-function-parameter-names-values-dynamically
    private fun functionArgumentNames(functionReference: dynamic): List<String> {
        val code = functionReference.toString()

        val withoutComments = JsParser.stripComments(code)
//        println("withoutComments: $withoutComments")

        val argumentDeclaration = JsParser.argumentDeclaration(withoutComments)
//        println("argumentDeclaration: $argumentDeclaration")

        val argumentList = JsParser.splitArgumentList(argumentDeclaration)
//        println("argumentList: $argumentList - ${argumentList.size}")

        return argumentList.map { JsParser.argumentName(it) }
    }


    private data class Metadata(
            val constructorFunction: dynamic,
            val constructorArgumentNames: List<String>)


    private object JsParser {
        private val functionArguments = Regex(
                "^function\\s*[^\\(]*\\(\\s*([^\\)]*)\\)",
                RegexOption.MULTILINE)

        private val comments = Regex(
                "((\\/\\/.*\$)|(\\/\\*[\\s\\S]*?\\*\\/))",
                RegexOption.MULTILINE)

        private val functionArgument = Regex("^\\s*(_?)(.+?)\\1\\s*$");


        fun stripComments(code: String): String =
                comments.replace(code, "")


        fun argumentDeclaration(functionCode: String): String =
                functionArguments.find(functionCode)!!.groupValues.get(1)


        fun splitArgumentList(argumentDeclaration: String): List<String> =
                if (argumentDeclaration.isBlank()) {
                    listOf()
                }
                else {
                    argumentDeclaration.split(',')
                }


        fun argumentName(argumentDeclaration: String): String =
                functionArgument.find(argumentDeclaration)!!.groups.get(2)!!.value
    }
}