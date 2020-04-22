package tech.kzen.lib.platform


object ClassNames {
    //-----------------------------------------------------------------------------------------------------------------
    fun ClassName.simple(): String {
        val qualified = get()
        val startOfSimple = qualified.lastIndexOf(".")
        return qualified.substring(startOfSimple + 1)
    }


    fun ClassName.nested(): String {
        val simple = simple()
        val startOfNested = simple.lastIndexOf("$")
        return simple.substring(startOfNested + 1)
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


//    fun ClassName.parent(): String {
//        val qualified = get()
//        val startOfSimple = qualified.lastIndexOf(".")
//        return qualified.substring(0, startOfSimple)
//    }


    //-----------------------------------------------------------------------------------------------------------------
    val kotlinAny = ClassName("kotlin.Any")
    val kotlinString = ClassName("kotlin.String")
    val kotlinBoolean = ClassName("kotlin.Boolean")
    val kotlinInt = ClassName("kotlin.Int")
    val kotlinDouble = ClassName("kotlin.Double")

    val kotlinList = ClassName("kotlin.collections.List")


    fun isPrimitive(className: ClassName): Boolean {
        return className == kotlinString ||
                className == kotlinBoolean ||
                className == kotlinInt ||
                className == kotlinDouble;
    }
}