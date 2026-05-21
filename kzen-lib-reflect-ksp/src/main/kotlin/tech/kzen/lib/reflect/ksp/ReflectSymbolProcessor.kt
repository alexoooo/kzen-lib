package tech.kzen.lib.reflect.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Variance


class ReflectSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val moduleClassName: String
) : SymbolProcessor {

    private val collected = mutableListOf<ReflectClass>()
    private val sourceFiles = mutableSetOf<KSFile>()
    private var emitted = false


    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(REFLECT_ANNOTATION_FQN)

        for (symbol in symbols) {
            if (symbol !is KSClassDeclaration) {
                logger.warn("@Reflect on non-class declaration is ignored", symbol)
                continue
            }
            val captured = capture(symbol) ?: continue
            collected.add(captured)
            symbol.containingFile?.let { sourceFiles.add(it) }
        }

        return emptyList()
    }


    override fun finish() {
        if (emitted) return
        emitted = true

        // Don't emit when nothing was collected — KSP runs on every source set (main and test by
        // default), and an empty test-side file collides with the main module's FQN on the test
        // classpath, shadowing real registrations.
        if (collected.isEmpty()) {
            return
        }

        val sorted = collected.sortedBy { it.registryName }
        val source = render(moduleClassName, sorted)

        val pkg = moduleClassName.substringBeforeLast('.', missingDelimiterValue = "")
        val simple = moduleClassName.substringAfterLast('.')

        val deps = Dependencies(aggregating = true, *sourceFiles.toTypedArray())
        codeGenerator.createNewFile(deps, pkg, simple).bufferedWriter().use { it.write(source) }
    }


    private fun capture(decl: KSClassDeclaration): ReflectClass? {
        val pkg = decl.packageName.asString()
        val nestedSimpleNames = nestedSimpleNames(decl)
        if (nestedSimpleNames.isEmpty()) {
            logger.error("Cannot determine nested name path for @Reflect class", decl)
            return null
        }

        val registryName =
            if (pkg.isEmpty()) nestedSimpleNames.joinToString("\$")
            else "$pkg.${nestedSimpleNames.joinToString("\$")}"

        val kotlinRef = nestedSimpleNames.joinToString(".")

        val imports = sortedSetOf<String>()
        val outerTopLevelImport =
            if (pkg.isEmpty()) nestedSimpleNames.first()
            else "$pkg.${nestedSimpleNames.first()}"
        imports.add(outerTopLevelImport)

        val isObject = decl.classKind == ClassKind.OBJECT

        val args: List<ReflectArg> = if (isObject) {
            emptyList()
        } else {
            val ctor = decl.primaryConstructor
            if (ctor == null || ctor.parameters.isEmpty()) {
                emptyList()
            } else {
                ctor.parameters.map { param ->
                    val name = param.name?.asString()
                    if (name == null) {
                        logger.error("Constructor parameter without a name in @Reflect class", param)
                        return null
                    }
                    val typeExpr = renderType(param.type.resolve(), imports)
                    ReflectArg(name, typeExpr)
                }
            }
        }

        return ReflectClass(registryName, kotlinRef, isObject, args, imports)
    }


    private fun nestedSimpleNames(decl: KSClassDeclaration): List<String> {
        val result = ArrayDeque<String>()
        var current: KSDeclaration? = decl
        while (current is KSClassDeclaration) {
            result.addFirst(current.simpleName.asString())
            current = current.parentDeclaration
        }
        return result.toList()
    }


    private fun renderType(type: KSType, importsOut: MutableSet<String>): String {
        val decl = type.declaration

        if (decl is KSTypeParameter) {
            return "Any" + if (type.nullability == Nullability.NULLABLE) "?" else ""
        }

        val classDecl = decl as? KSClassDeclaration
            ?: return "Any" + if (type.nullability == Nullability.NULLABLE) "?" else ""

        val pkg = classDecl.packageName.asString()
        val nested = nestedSimpleNames(classDecl)
        if (nested.isEmpty()) {
            return "Any" + if (type.nullability == Nullability.NULLABLE) "?" else ""
        }

        val outerSimple = nested.first()
        if (pkg.isNotEmpty() && !isAutoImportedPackage(pkg)) {
            importsOut.add("$pkg.$outerSimple")
        }

        val ref = nested.joinToString(".")

        val typeArgs = type.arguments
        val argsStr = if (typeArgs.isEmpty()) {
            ""
        } else {
            typeArgs.joinToString(", ", "<", ">") { ksArg ->
                if (ksArg.variance == Variance.STAR || ksArg.type == null) {
                    "*"
                } else {
                    val resolved = ksArg.type!!.resolve()
                    val rendered = renderType(resolved, importsOut)
                    when (ksArg.variance) {
                        Variance.COVARIANT -> "out $rendered"
                        Variance.CONTRAVARIANT -> "in $rendered"
                        else -> rendered
                    }
                }
            }
        }

        val nullableSuffix = if (type.nullability == Nullability.NULLABLE) "?" else ""
        return "$ref$argsStr$nullableSuffix"
    }


    private fun isAutoImportedPackage(pkg: String): Boolean {
        return pkg == "kotlin" ||
            pkg == "kotlin.collections" ||
            pkg == "kotlin.ranges" ||
            pkg == "kotlin.sequences" ||
            pkg == "kotlin.text" ||
            pkg == "kotlin.io" ||
            pkg == "kotlin.annotation" ||
            pkg == "kotlin.comparisons" ||
            pkg == "kotlin.jvm"
    }


    private fun render(moduleFqn: String, classes: List<ReflectClass>): String {
        val outputPkg = moduleFqn.substringBeforeLast('.', missingDelimiterValue = "")
        val outputSimple = moduleFqn.substringAfterLast('.')

        val imports = sortedSetOf<String>()
        imports.add("tech.kzen.lib.common.reflect.ReflectionRegistry")
        imports.add("tech.kzen.lib.common.reflect.ModuleReflection")
        for (c in classes) {
            for (imp in c.imports) {
                if (imp.substringBeforeLast('.', missingDelimiterValue = "") != outputPkg) {
                    imports.add(imp)
                }
            }
        }
        val importsBlock = imports.joinToString("\n") { "import $it" }

        val registrations = classes.joinToString("\n\n") { c -> renderRegistration(c) }

        val body = if (classes.isEmpty()) "" else "\n$registrations\n"

        return buildString {
            append("// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ReflectSymbolProcessor (KSP)\n")
            if (outputPkg.isNotEmpty()) {
                append("package $outputPkg\n\n")
            }
            append(importsBlock)
            append("\n\n\n")
            append("@Suppress(\"UNCHECKED_CAST\", \"KotlinRedundantDiagnosticSuppress\")\n")
            append("object $outputSimple: ModuleReflection {\n")
            append("    override fun register(reflectionRegistry: ReflectionRegistry) {")
            append(body)
            append("    }\n")
            append("}\n")
        }
    }


    private fun renderRegistration(c: ReflectClass): String {
        val classNameLit = "\"${escapeKotlinStringLiteral(c.registryName)}\""
        val argNamesList = c.arguments.joinToString(", ") { "\"${it.name}\"" }

        return when {
            c.isObject -> """
                |reflectionRegistry.put(
                |    $classNameLit,
                |    listOf($argNamesList)
                |) {
                |    ${c.kotlinReference}
                |}
            """.trimMargin()
            c.arguments.isEmpty() -> """
                |reflectionRegistry.put(
                |    $classNameLit,
                |    listOf()
                |) {
                |    ${c.kotlinReference}()
                |}
            """.trimMargin()
            else -> {
                val argsCast = c.arguments.withIndex()
                    .joinToString(", ") { (i, a) -> "args[$i] as ${a.typeExpr}" }
                """
                |reflectionRegistry.put(
                |    $classNameLit,
                |    listOf($argNamesList)
                |) { args ->
                |    ${c.kotlinReference}($argsCast)
                |}
                """.trimMargin()
            }
        }
    }


    private fun escapeKotlinStringLiteral(raw: String): String {
        return raw.replace("\$", "\\\$")
    }


    private data class ReflectClass(
        val registryName: String,
        val kotlinReference: String,
        val isObject: Boolean,
        val arguments: List<ReflectArg>,
        val imports: Set<String>
    )


    private data class ReflectArg(
        val name: String,
        val typeExpr: String
    )


    companion object {
        private const val REFLECT_ANNOTATION_FQN = "tech.kzen.lib.common.reflect.Reflect"
    }
}
