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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.stream.Collectors


// TODO: handle trailing comma in constructor
object ModuleReflectionGenerator
{
    //-----------------------------------------------------------------------------------------------------------------
    private const val kotlinExtension = ".kt"
    private const val packagePrefix = "package "
    private const val importPrefix = "import "
    private const val starImportSuffix = ".*"
    private const val classPrefix = "class "
    private const val objectPrefix = "object "

    private val inlineCommentPattern = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)


    //-----------------------------------------------------------------------------------------------------------------
    fun generate(
        relativeSourceDir: Path,
        moduleReflectionName: ClassName,
        vararg dependencySourceDirs: Path
    ) {
        val sourceDir = projectHome().resolve(relativeSourceDir)
        val reflectSources = scanReflectSources(sourceDir)
        val reflectConstructors = reflectConstructors(
            relativeSourceDir, reflectSources, dependencySourceDirs.asList())

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
        val withoutInline = inlineCommentPattern.replace(sourceCode, "")
        return withoutInline
            .split("\n")
            .joinToString("\n") {
                val commentIndex = it.indexOf("//")
                if (commentIndex == -1) {
                    it
                } else {
                    it.substring(0, commentIndex)
                }
            }
    }


    private fun filterReflectSource(sourceCode: String): Boolean {
        return sourceCode.contains("$importPrefix${Reflect.qualifiedName}") &&
                sourceCode.contains("@${Reflect.simpleName}")
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun reflectConstructors(
        relativeSourceDir: Path,
        reflectSources: Map<Path, String>,
        dependencySourceDirs: List<Path>
    ): Map<ClassName, ConstructorReflection> {
        val builder = mutableMapOf<ClassName, ConstructorReflection>()

        for ((sourceFile, sourceCode) in reflectSources) {
//            if (sourceFile.fileName.toString() == "ReportDocument.kt") {
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
                val constructorReflection = reflectConstructor(
                    relativeSourceDir, sourceClassName, sourceFile, sourceCode, dependencySourceDirs)

                check(sourceClassName !in builder) { "Already exists: $sourceClassName" }
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
//            val startOfClass = sourceCode.indexOf(classPrefix, nextReflectAnnotationIndex) + classPrefix.length
            val startOfClass = nextClassOrObject(sourceCode, nextReflectAnnotationIndex)
            check(startOfClass != -1)

            val endOfClass = sourceCode.indexOfAny(" :\r\n<({".toCharArray(), startOfClass)

            val simpleName = sourceCode.substring(startOfClass, endOfClass).trim()

            builder.add(ClassName(sourceClassName.get() + "$" + simpleName))

            nextReflectAnnotationIndex = sourceCode.indexOf(
                    "@${Reflect.simpleName}", nextReflectAnnotationIndex + 1)
        }

        return builder
    }


    private fun nextClassOrObject(sourceCode: String, startIndex: Int): Int {
        val classPrefixIndex = sourceCode.indexOf(classPrefix, startIndex)
        val objectPrefixIndex = sourceCode.indexOf(objectPrefix, startIndex)

        if (objectPrefixIndex == -1 || classPrefixIndex != -1 && classPrefixIndex < objectPrefixIndex) {
            return classPrefixIndex + classPrefix.length
        }

        if (objectPrefixIndex != -1) {
            return objectPrefixIndex + objectPrefix.length
        }

        return -1
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun reflectConstructor(
        relativeSourceDir: Path,
        sourceClass: ClassName,
        sourceFile: Path,
        sourceCode: String,
        dependencySourceDirs: List<Path>
    ):
            ConstructorReflection
    {
        val nestedName = sourceClass.nested()
        val nestedNameMatch = Regex(
                "($classPrefix|$objectPrefix)" + Regex.escape(nestedName) + "(\\W|$)",
                RegexOption.MULTILINE
        ).find(sourceCode)
                ?: throw IllegalArgumentException("Unable to find: $sourceClass")

        val startOfConstructorName = nestedNameMatch.range.first
        val endOfConstructorName = nestedNameMatch.range.last

        if (sourceCode.length <= endOfConstructorName) {
            return zeroParameterConstructor(sourceCode, startOfConstructorName)
        }

        val afterConstructor = sourceCode.substring(endOfConstructorName)
        val typeParameterMatch =
            Regex("^((\\s|\r|\n)*<).*", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
                .matchEntire(afterConstructor)

        val typeParameters: List<String>
        val beforeConstructorParams: Int
        if (typeParameterMatch == null) {
            typeParameters = listOf()
            beforeConstructorParams = endOfConstructorName
        }
        else {
            val startOfParameterList = endOfConstructorName + typeParameterMatch.groups[1]!!.range.last + 1
            val endOfParameterList = sourceCode
                .indexOf('>', startOfParameterList)

            val parameterList = sourceCode.substring(startOfParameterList, endOfParameterList)
            typeParameters = parameterList.split(',').map { it.trim() }
            beforeConstructorParams = endOfParameterList + 1
        }

        if (sourceCode.length <= beforeConstructorParams) {
            return zeroParameterConstructor(sourceCode, startOfConstructorName, typeParameters)
        }

        val afterClassNameAndGenerics = sourceCode.substring(beforeConstructorParams)
        val openConstructorMatch =
            Regex("^((\\s|\r|\n)*[(]).*", setOf(RegexOption.DOT_MATCHES_ALL))
                    .find(afterClassNameAndGenerics)
                ?: return zeroParameterConstructor(sourceCode, startOfConstructorName, typeParameters)

        val startOfParams = beforeConstructorParams + openConstructorMatch.groups[1]!!.range.last + 1
        val endOfParams = findMatchingBracket(sourceCode, startOfParams, '(', ')')
        val arguments = sourceCode.substring(startOfParams, endOfParams)

        if (":" !in arguments) {
            return ConstructorReflection.emptyClass
        }

        val argumentList = arguments.split(",")
        val hasTrailingComma = ":" !in argumentList.last()

        val argumentsWithoutTrailing =
            if (hasTrailingComma) {
                argumentList.dropLast(1)
            }
            else {
                argumentList
            }

        val argumentReflections = argumentsWithoutTrailing.map {
            val endOfName = it.indexOf(":")

            val rawArgumentName = it.substring(0, endOfName).trim().substringAfterLast(' ')
            val argumentName = unescapeArgumentName(rawArgumentName)

            val argumentType = it.substring(endOfName + 1).trim()

            val typeImports = findImports(
                relativeSourceDir, argumentType, sourceClass, sourceFile, sourceCode, dependencySourceDirs)

            ArgumentReflection(argumentName, argumentType, typeImports)
        }

        return ConstructorReflection.ofClass(argumentReflections, typeParameters)
    }


    private fun unescapeArgumentName(rawArgumentName: String): String {
        if (! rawArgumentName.startsWith('`')) {
            return rawArgumentName
        }
        return rawArgumentName.substring(1, rawArgumentName.length - 1)
    }


    private fun zeroParameterConstructor(
        sourceCode: String,
        startOfConstructor: Int,
        typeParameters: List<String> = listOf()
    ): ConstructorReflection {
        val startingWithConstructor = sourceCode.substring(startOfConstructor)
        return when {
            startingWithConstructor.startsWith(objectPrefix) -> {
                check(typeParameters.isEmpty())
                ConstructorReflection.emptyObject
            }

            typeParameters.isEmpty() -> {
                ConstructorReflection.emptyClass
            }

            else -> {
                ConstructorReflection(listOf(), typeParameters, false)
            }
        }
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
        relativeSourceDir: Path,
        argumentType: String,
        sourceClass: ClassName,
        sourceFile: Path,
        sourceCode: String,
        dependencySourceDirs: List<Path>
    ): Set<ClassName> {
        val builder = mutableSetOf<ClassName>()

        val typeComponents = argumentType
            .split(Regex("\\W+"))
            .filter { it.isNotEmpty() }

        for (typeComponent in typeComponents) {
            val matchingImports = findImportStatements(typeComponent, sourceCode)
            builder.addAll(matchingImports)

            val matchingNestedSiblings = findNestedSiblings(typeComponent, sourceClass, sourceCode)
            builder.addAll(matchingNestedSiblings)

            val matchingSiblings = findSiblingClasses(typeComponent, sourceClass, sourceFile)
            builder.addAll(matchingSiblings)

            val matchingStarImports = findStarImports(typeComponent, sourceCode, relativeSourceDir, dependencySourceDirs)
            builder.addAll(matchingStarImports)
        }

        return builder
    }


    private fun findImportStatements(
            argumentType: String,
            sourceCode: String
    ): Set<ClassName> {
        val importedPaths = sourceCode
            .split("\n")
            .filter { it.startsWith(importPrefix) }
            .map { it.substring(importPrefix.length).trim() }

        val builder = mutableSetOf<ClassName>()

        for (importPath in importedPaths) {
            val suffix = importPath.substring(importPath.lastIndexOf(".") + 1)

            if (suffix.matches(Regex("^\\w+$")) &&
                    argumentType.matches(Regex("\\b" + Regex.escape(suffix) + "\\b")))
            {
                builder.add(ClassName(importPath))
            }
        }

        return builder
    }


    private fun findSiblingClasses(
            argumentType: String,
            sourceClass: ClassName,
            sourceFile: Path
    ): Set<ClassName> {
        if (! Files.exists(sourceFile)) {
            return setOf()
        }

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


    private fun findStarImports(
        argumentType: String,
        sourceCode: String,
        relativeSourceDir: Path,
        dependencySourceDirs: List<Path>
    ): Set<ClassName> {
        val starImportedPaths = sourceCode
            .split("\n")
            .filter { it.startsWith(importPrefix) }
            .map { it.trim() }
            .filter { it.endsWith(starImportSuffix) }
            .map { it.substring(importPrefix.length, it.length - starImportSuffix.length) }

//        if (! Files.exists(sourceFile)) {
//            return setOf()
//        }
//
//        return Files
//            .list(sourceFile.parent)
//            .use { dir ->
//                dir.map { it.fileName.toString() }
//                    .filter { it.endsWith(kotlinExtension) }
//                    .map { it.substring(0, it.length - kotlinExtension.length) }
//                    .collect(Collectors.toList())
//            }
//            .filter { argumentType.contains(it) }
//            .map { ClassName(sourceClass.packageName() + ".$it") }
//            .toSet()

        val sourceDirectories =
            dependencySourceDirs + listOf(relativeSourceDir)

//        val builder = mutableSetOf<ClassName>()

        for (starImportPath in starImportedPaths) {
            val codePath = Paths.get(
                starImportPath.replace('.', '/'))

            for (sourceDir in sourceDirectories) {
                val resolvedPath = sourceDir.resolve(codePath).resolve("$argumentType.kt")
                if (Files.exists(resolvedPath)) {
                    return setOf(ClassName(
                        "$starImportPath.$argumentType"))
                }
            }
        }

        return setOf()
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
                                .withIndex()
                                .joinToString(", ") {
                                    "args[${it.index}] as ${it.value.externalType(e.key, e.value.typeParameters)}"
                                }

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

        val header = "// **DO NOT EDIT, CHANGES WILL BE LOST** -" +
                " automatically generated by ModuleReflectionGenerator at " +
                "${LocalDateTime.now()}"

        return """$header
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