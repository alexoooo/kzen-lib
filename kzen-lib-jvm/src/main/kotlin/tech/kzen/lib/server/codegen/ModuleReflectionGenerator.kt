package tech.kzen.lib.server.codegen

import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.packageName
import tech.kzen.lib.platform.ClassNames.simple
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption


object ModuleReflectionGenerator
{
    //-----------------------------------------------------------------------------------------------------------------
    private const val kotlinExtension = ".kt"
    private const val packagePrefix = "package "


    //-----------------------------------------------------------------------------------------------------------------
    fun generate(
            relativeSourceDir: Path,
            moduleReflectionName: ClassName
    ) {
        val sourceDir = projectHome().resolve(relativeSourceDir)
        val reflectSources = scanReflectSources(sourceDir)
        val reflectConstructors = reflectConstructors(reflectSources)

        val moduleReflection = generateModuleReflection(
                moduleReflectionName, reflectConstructors)

        write(sourceDir, moduleReflectionName, moduleReflection)
    }


    private fun write(sourceDir: Path, moduleReflectionName: ClassName, generatedCode: String) {
        val generatedPath = sourceDir.resolve(
                moduleReflectionName.get().replace(".", "/") + kotlinExtension)

        println("Writing: ${generatedPath.toAbsolutePath().normalize()}")

        Files.createDirectories(generatedPath.parent)

        Files.writeString(
                generatedPath, generatedCode,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun projectHome(): Path {
        return Path.of(".").toAbsolutePath()
    }


    private fun scanReflectSources(sourceDir: Path): Map<Path, String> {
        return Files.walk(sourceDir).use { stream ->
            val builder = mutableMapOf<Path, String>()

            stream.filter {
                it.fileName.toString().endsWith(kotlinExtension)
            }.forEach {
                val content = Files.readString(it)
                val source = removeComments(content)

                if (filterReflectSource(source)) {
                    builder[it] = source
                }
            }

            builder
        }
    }


    private fun removeComments(sourceCode: String): String {
        return sourceCode
                .split("\n")
                .filterNot { it.trim().startsWith("//") }
                .joinToString("\n")
    }


    private fun filterReflectSource(sourceCode: String): Boolean {
        return sourceCode.contains("import ${Reflect.qualifiedName}") &&
                sourceCode.contains("@${Reflect.simpleName}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reflectConstructors(
            reflectSources: Map<Path, String>
    ): Map<ClassName, ConstructorReflection> {
        val builder = mutableMapOf<ClassName, ConstructorReflection>()

        for ((sourceFile, sourceCode) in reflectSources) {
            val packageIndex = sourceCode.indexOf("package ")
            val startOfPackage = packageIndex + packagePrefix.length

            val endOfPackage = sourceCode.indexOf("\n", startOfPackage)
            val packagePath = sourceCode.substring(startOfPackage, endOfPackage).trim()

            val fileName = sourceFile.fileName.toString()
            val simpleName = fileName.substring(0, fileName.length - kotlinExtension.length)

            val className = ClassName("$packagePath.$simpleName")
            val constructorReflection = reflectConstructor(className, sourceCode)

            builder[className] = constructorReflection
        }

        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun reflectConstructor(
            className: ClassName, sourceCode: String
    ): ConstructorReflection {
        val simpleName = className.simple()
        val startOfConstructor = sourceCode.indexOf(simpleName)
        val startOfParams = startOfConstructor + simpleName.length

        val paramOpenBracket = sourceCode[startOfParams]
        if (paramOpenBracket != '(') {
            val beforeConstructor = sourceCode.substring(0, startOfConstructor)
            return if (beforeConstructor.endsWith("object ")) {
                ConstructorReflection.emptyObject
            }
            else {
                ConstructorReflection.emptyClass
            }
        }

        val endOfParams = sourceCode.indexOf(')', startIndex = startOfParams + 1)
        val arguments = sourceCode.substring(startOfParams + 1, endOfParams)

        if (! arguments.contains(":")) {
            return ConstructorReflection.emptyClass
        }

        val argumentList = arguments.split(",")

        val argumentPairs = argumentList.map {
            val endOfName = it.indexOf(":")
            val argumentName = it.substring(0, endOfName).trim().substringAfterLast(' ')
            val argumentType = it.substring(endOfName + 1).trim()
            argumentName to argumentType
        }

        return ConstructorReflection.ofClass(argumentPairs)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun generateModuleReflection(
            moduleReflectionName: ClassName,
            reflectConstructors: Map<ClassName, ConstructorReflection>
    ): String {
        val registerStatements = mutableListOf<String>()

        val imports = mutableSetOf<ClassName>()

        imports.add(ReflectionRegistry.className)
        imports.add(ModuleReflection.className)

        for (e in reflectConstructors) {
            imports.add(e.key)

            val argumentNames = e.value.arguments.map { generateString(it.first) }

            val argsDeclaration =
                    if (e.value.arguments.isEmpty()) {
                        ""
                    }
                    else {
                        " args ->"
                    }

            val instantiation =
                    if (e.value.isObject) {
                        e.key.simple()
                    }
                    else {
                        val argumentCast = e
                                .value
                                .arguments
                                .map { it.second }
                                .withIndex()
                                .joinToString(", ") { "args[${it.index}] as ${it.value}" }

                        e.key.simple() + "($argumentCast)"
                    }

            registerStatements.add("""
                reflectionRegistry.put(
                    ${generateString(e.key.get())},
                    listOf(${argumentNames.joinToString(", ")})
                ) {$argsDeclaration
                    $instantiation
                }
            """.trimIndent())
        }

        return """
// **DO NOT EDIT, CHANGES WILL BE LOST** automatically generated by ModuleReflectionGenerator
package ${moduleReflectionName.packageName()}

${imports.joinToString("\n") { "import ${it.get()}" }}

object ${moduleReflectionName.simple()}: ${ModuleReflection.simpleName} {
    override fun register(reflectionRegistry: ReflectionRegistry) {
${registerStatements.joinToString("\n\n")}
    }
}
"""
    }

    private fun generateString(rawValue: String): String {
        return "\"$rawValue\""
    }
}