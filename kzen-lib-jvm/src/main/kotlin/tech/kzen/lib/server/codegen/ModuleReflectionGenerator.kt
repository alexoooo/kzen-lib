package tech.kzen.lib.server.codegen

import tech.kzen.lib.common.reflect.ModuleReflection
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.ClassNames.asTopLevelImport
import tech.kzen.lib.platform.ClassNames.nested
import tech.kzen.lib.platform.ClassNames.nestedInSimple
import tech.kzen.lib.platform.ClassNames.packageName
import tech.kzen.lib.platform.ClassNames.simple
import java.lang.IllegalArgumentException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors


object ModuleReflectionGenerator
{
    //-----------------------------------------------------------------------------------------------------------------
    private const val kotlinExtension = ".kt"
    private const val packagePrefix = "package "
    private const val importPrefix = "import "
    private const val classPrefix = "class "
    private const val objectPrefix = "object "


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
        return sourceCode.contains("$importPrefix${Reflect.qualifiedName}") &&
                sourceCode.contains("@${Reflect.simpleName}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reflectConstructors(
            reflectSources: Map<Path, String>
    ): Map<ClassName, ConstructorReflection> {
        val builder = mutableMapOf<ClassName, ConstructorReflection>()

        for ((sourceFile, sourceCode) in reflectSources) {
//            if (sourceFile.fileName.toString() == "NestedObject.kt") {
//                println("foo")
//            }

            val packageIndex = sourceCode.indexOf("package ")
            val startOfPackage = packageIndex + packagePrefix.length

            val endOfPackage = sourceCode.indexOf("\n", startOfPackage)
            val packagePath = sourceCode.substring(startOfPackage, endOfPackage).trim()

            val fileName = sourceFile.fileName.toString()
            val simpleName = fileName.substring(0, fileName.length - kotlinExtension.length)

            val topClassName = ClassName("$packagePath.$simpleName")
            val sourceClassName = reflectedClassName(topClassName, sourceCode)

            val constructorReflection =
                    reflectConstructor(sourceClassName, sourceFile, sourceCode)

            builder[sourceClassName] = constructorReflection
        }

        return builder
    }


    private fun reflectedClassName(
            sourceClassName: ClassName,
            sourceCode: String
    ): ClassName {
        val reflectAnnotationIndex = sourceCode.indexOf("@${Reflect.simpleName}")
        val sourceConstructorIndex = sourceCode.indexOf(sourceClassName.simple())

        if (reflectAnnotationIndex < sourceConstructorIndex) {
            return sourceClassName
        }

        val startOfClass = sourceCode.indexOf(classPrefix, reflectAnnotationIndex) + classPrefix.length
        val endOfClass = sourceCode.indexOfAny(" \r\n({".toCharArray(), startOfClass)

        val simpleName = sourceCode.substring(startOfClass, endOfClass).trim()

        return ClassName(sourceClassName.get() + "$" + simpleName)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun reflectConstructor(
        sourceClass: ClassName,
        sourceFile: Path,
        sourceCode: String
    ):
            ConstructorReflection
    {
        val nestedName = sourceClass.nested()
        val nestedNameMatch = Regex(
                "\\W" + Regex.escape(nestedName) + "(\\W|$)",
                RegexOption.MULTILINE
        ).find(sourceCode)
                ?: throw IllegalArgumentException("Unable to find: $sourceClass")

//        val startOfConstructor = sourceCode.indexOf(nestedName)
//        val startOfParams = startOfConstructor + nestedName.length
        val startOfConstructor = nestedNameMatch.range.first
        val startOfParams = nestedNameMatch.range.last

        if (sourceCode.length <= startOfParams ||
            sourceCode[startOfParams] != '(')
        {
            val beforeConstructor = sourceCode.substring(0, startOfConstructor)
            return if (beforeConstructor.endsWith(objectPrefix)) {
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

            val typeImports = findImports(argumentType, sourceClass, sourceFile, sourceCode)
            ArgumentReflection(argumentName, argumentType, typeImports)
        }

        return ConstructorReflection.ofClass(argumentPairs)
    }


    private fun findImports(
        argumentType: String,
        sourceClass: ClassName,
        sourceFile: Path,
        sourceCode: String
    ): Set<ClassName> {
        val builder = mutableSetOf<ClassName>()

        val importedPaths = sourceCode
            .split("\n")
            .filter { it.startsWith(importPrefix) }
            .map { it.substring(importPrefix.length).trim() }

        for (importPath in importedPaths) {
            val suffix = importPath.substring(importPath.lastIndexOf(".") + 1)
            if (argumentType.contains(suffix)) {
                builder.add(ClassName(importPath))
            }
        }

        val siblingClasses = Files
            .list(sourceFile.parent)
            .use { dir ->
                dir.map { it.fileName.toString() }
                    .filter { it.endsWith(kotlinExtension) }
                    .map { it.substring(0, it.length - kotlinExtension.length) }
                    .collect(Collectors.toList())
            }

        for (siblingClass in siblingClasses) {
            if (argumentType.contains(siblingClass)) {
                val simplingQualifiedName = sourceClass.packageName() + ".$siblingClass"
                builder.add(ClassName(simplingQualifiedName))
            }
        }

        return builder
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
            imports.addAll(e.value.arguments.flatMap { it.typeClasses })

            val argumentNames = e.value.arguments.map { generateString(it.name) }

            val argsDeclaration =
                    if (e.value.arguments.isEmpty()) {
                        ""
                    }
                    else {
                        " args ->"
                    }

            val instantiation =
                    if (e.value.isObject) {
                        e.key.nestedInSimple()
                    }
                    else {
                        val argumentCast = e
                                .value
                                .arguments
                                .map { it.type }
                                .withIndex()
                                .joinToString(", ") { "args[${it.index}] as ${it.value}" }

                        e.key.nestedInSimple() + "($argumentCast)"
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

        val uniqueTopLevelImports = imports
                .map { it.asTopLevelImport() }
                .toSet()
                .joinToString("\n") { "$importPrefix$it" }

        return """
// **DO NOT EDIT, CHANGES WILL BE LOST** automatically generated by ModuleReflectionGenerator
package ${moduleReflectionName.packageName()}

$uniqueTopLevelImports


@Suppress("UNCHECKED_CAST")
object ${moduleReflectionName.simple()}: ${ModuleReflection.simpleName} {
    override fun register(reflectionRegistry: ReflectionRegistry) {
${registerStatements.joinToString("\n\n")}
    }
}
"""
    }

    private fun generateString(rawValue: String): String {
        return "\"${rawValue.replace("$", "\\$")}\""
    }
}