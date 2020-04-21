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
import tech.kzen.lib.platform.ClassNames.topLevel
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
            val sourceClassNames = reflectedClassNames(topClassName, sourceCode)

            for (sourceClassName in sourceClassNames) {
                val constructorReflection =
                        reflectConstructor(sourceClassName, sourceFile, sourceCode)

                builder[sourceClassName] = constructorReflection
            }
        }

        return builder
    }


    private fun reflectedClassNames(
            sourceClassName: ClassName,
            sourceCode: String
    ): List<ClassName> {
        val firstReflectAnnotationIndex = sourceCode.indexOf("@${Reflect.simpleName}")
        val sourceConstructorIndex = sourceCode.indexOf(sourceClassName.simple())

        if (firstReflectAnnotationIndex < sourceConstructorIndex) {
            return listOf(sourceClassName)
        }

        val builder = mutableListOf<ClassName>()
        var nextReflectAnnotationIndex = firstReflectAnnotationIndex
        while (nextReflectAnnotationIndex != -1) {
            val startOfClass = sourceCode.indexOf(classPrefix, nextReflectAnnotationIndex) + classPrefix.length
            val endOfClass = sourceCode.indexOfAny(" \r\n({".toCharArray(), startOfClass)

            val simpleName = sourceCode.substring(startOfClass, endOfClass).trim()

            builder.add(ClassName(sourceClassName.get() + "$" + simpleName))

            nextReflectAnnotationIndex = sourceCode.indexOf(
                    "@${Reflect.simpleName}", nextReflectAnnotationIndex + 1)
        }

        return builder
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
                "($classPrefix|$objectPrefix)" + Regex.escape(nestedName) + "(\\W|$)",
                RegexOption.MULTILINE
        ).find(sourceCode)
                ?: throw IllegalArgumentException("Unable to find: $sourceClass")

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

        val endOfParams = findMatchingBracket(sourceCode, startOfParams + 1, '(', ')')
        val arguments = sourceCode.substring(startOfParams + 1, endOfParams)

        if (! arguments.contains(":")) {
            return ConstructorReflection.emptyClass
        }

        val argumentList = arguments.split(",")

        val argumentReflections = argumentList.map {
            val endOfName = it.indexOf(":")
            val argumentName = it.substring(0, endOfName).trim().substringAfterLast(' ')
            val argumentType = it.substring(endOfName + 1).trim()

            val typeImports = findImports(argumentType, sourceClass, sourceFile, sourceCode)
            ArgumentReflection(argumentName, argumentType, typeImports)
        }

        return ConstructorReflection.ofClass(argumentReflections)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun findMatchingBracket(
            sourceCode: String,
            startIndex: Int,
            open: Char,
            close: Char
    ): Int {
        val openClose: CharArray = charArrayOf(open, close)
        val nextIndex = sourceCode.indexOfAny(openClose, startIndex)

        if (nextIndex == -1) {
            return -1
        }

        val next = sourceCode[nextIndex]

        if (next == close) {
            return nextIndex
        }

        val endOfNested = findMatchingBracket(sourceCode, nextIndex + 1, open, close)

        return findMatchingBracket(sourceCode, endOfNested + 1, open, close)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun findImports(
        argumentType: String,
        sourceClass: ClassName,
        sourceFile: Path,
        sourceCode: String
    ): Set<ClassName> {
        val builder = mutableSetOf<ClassName>()

        val matchingImports = findImportStatements(argumentType, sourceCode)
        builder.addAll(matchingImports)

        val matchingSiblings = findSiblingClasses(argumentType, sourceClass, sourceFile)
        builder.addAll(matchingSiblings)

        val matchingNestedSiblings = findNestedSiblings(argumentType, sourceClass, sourceCode)
        builder.addAll(matchingNestedSiblings)

        return builder
    }


    private fun findImportStatements(
            argumentType: String,
            sourceCode: String
    ): Set<ClassName> {
        return sourceCode
                .split("\n")
                .asSequence()
                .filter { it.startsWith(importPrefix) }
                .map { it.substring(importPrefix.length).trim() }
                .map { it.substring(it.lastIndexOf(".") + 1) }
                .filter { argumentType.contains(it) }
                .map { ClassName(it) }
                .toSet()
    }


    private fun findSiblingClasses(
            argumentType: String,
            sourceClass: ClassName,
            sourceFile: Path
    ): Set<ClassName> {
        return Files
                .list(sourceFile.parent)
                .use { dir ->
                    dir.map { it.fileName.toString() }
                            .filter { it.endsWith(kotlinExtension) }
                            .map { it.substring(0, it.length - kotlinExtension.length) }
                            .collect(Collectors.toList())
                }
                .filter { argumentType.contains(it) }
                .map { ClassName(sourceClass.packageName() + ".$it") }
                .toSet()
    }


    private fun findNestedSiblings(
            argumentType: String,
            sourceClass: ClassName,
            sourceCode: String
    ): Set<ClassName> {
        Regex("($classPrefix|$objectPrefix)" + Regex.escape(argumentType) + "(\\W|$)",
                RegexOption.MULTILINE
        ).find(sourceCode)
            ?: return setOf()

        return setOf(ClassName(
                sourceClass.packageName() + "." + sourceClass.topLevel() + "$" + argumentType))
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