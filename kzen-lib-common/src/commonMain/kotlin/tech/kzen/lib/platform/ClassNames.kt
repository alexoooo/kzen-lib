package tech.kzen.lib.platform


@Suppress("unused")
object ClassNames {
    //-----------------------------------------------------------------------------------------------------------------
    val kotlinUnit = ClassName("kotlin.Unit")
    val kotlinAny = ClassName("kotlin.Any")
    val kotlinString = ClassName("kotlin.String")
    val kotlinBoolean = ClassName("kotlin.Boolean")
    val kotlinInt = ClassName("kotlin.Int")
    val kotlinLong = ClassName("kotlin.Long")
    val kotlinDouble = ClassName("kotlin.Double")

    val kotlinList = ClassName("kotlin.collections.List")
    val kotlinSet = ClassName("kotlin.collections.Set")


    fun isPrimitive(className: ClassName): Boolean {
        return className == kotlinString ||
                className == kotlinBoolean ||
                className == kotlinInt ||
                className == kotlinLong ||
                className == kotlinDouble
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun ClassName.simple(): String {
        val qualified = asString()
        val startOfSimple = qualified.lastIndexOf(".")
        return qualified.substring(startOfSimple + 1)
    }


    fun ClassName.nested(): String {
        val simple = simple()
        val startOfNested = simple.lastIndexOf("$")
        return simple.substring(startOfNested + 1)
    }


//    @OptIn(ExperimentalStdlibApi::class)
    fun ClassName.topLevelWords(): List<String> {
        val simpleName = topLevel()

        val commaSeparated = simpleName.replace(Regex("[A-Z]")) {
            if (it.range.first == 0) {
                it.value
            }
            else {
                ",${it.value}"
            }
        }

        return commaSeparated.split(',')
    }

    fun ClassName.topLevel(): String {
        val simple = simple()
        val endOfTopLevel = simple.indexOf("$")
        return if (endOfTopLevel == -1) {
            simple
        }
        else {
            simple.substring(0, endOfTopLevel)
        }
    }


    fun ClassName.nestedInSimple(): String {
        return simple().replace('$', '.')
    }


    fun ClassName.asImport(): String {
        return get().replace('$', '.')
    }


    fun ClassName.asTopLevelImport(): String {
        return packageName() + "." + topLevel()
    }


    fun ClassName.packageName(): String {
        val qualified = get()
        val startOfSimple = qualified.lastIndexOf(".")
        return qualified.substring(0, startOfSimple)
    }
}