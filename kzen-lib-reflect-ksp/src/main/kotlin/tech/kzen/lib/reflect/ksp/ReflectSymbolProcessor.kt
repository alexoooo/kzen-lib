package tech.kzen.lib.reflect.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*


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
            if (symbol.origin == Origin.JAVA || symbol.origin == Origin.JAVA_LIB) {
                // Java classes have no primary constructor here, so they are served by the JVM
                // reflective fallback instead; KSP registration is Kotlin-only
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
        if (Modifier.INNER in decl.modifiers) {
            logger.error(
                "@Reflect is not supported on inner classes (the generated constructor call would " +
                        "require an outer receiver): " + decl.qualifiedName?.asString(),
                decl)
            return null
        }

        val pkg = decl.packageName.asString()
        val nestedSimpleNames = nestedSimpleNames(decl)
        if (nestedSimpleNames.isEmpty()) {
            logger.error("Cannot determine nested name path for @Reflect class", decl)
            return null
        }

        val registryName =
            if (pkg.isEmpty()) nestedSimpleNames.joinToString("\$")
            else "$pkg.${nestedSimpleNames.joinToString("\$")}"

        val kotlinRef = qualifiedReference(pkg, nestedSimpleNames)

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
                    val resolvedType = param.type.resolve()
                    val typeExpr = renderType(resolvedType)

                    val isService = param.annotations.any { annotation ->
                        annotation.annotationType.resolve().declaration.qualifiedName?.asString() ==
                                SERVICE_ANNOTATION_FQN
                    }
                    val serviceTypeQualifiedName =
                        if (! isService) {
                            null
                        }
                        else {
                            resolvedType.declaration.qualifiedName?.asString()
                                ?: run {
                                    logger.error("@Service parameter type without a qualified name", param)
                                    return null
                                }
                        }

                    ReflectArg(name, typeExpr, serviceTypeQualifiedName)
                }
            }
        }

        return ReflectClass(registryName, kotlinRef, isObject, args)
    }


    private fun qualifiedReference(pkg: String, nestedSimpleNames: List<String>): String {
        val nestedReference = nestedSimpleNames.joinToString(".")
        return if (pkg.isEmpty()) nestedReference else "$pkg.$nestedReference"
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


    /**
     * Every type is rendered fully qualified, so the generated file needs no imports at all and two
     * constructor parameters whose types share a simple name can't collide.
     */
    private fun renderType(type: KSType): String {
        val nullableSuffix = if (type.nullability == Nullability.NULLABLE) "?" else ""
        val erasedReference = "kotlin.Any$nullableSuffix"

        val decl = type.declaration
        if (decl is KSTypeParameter) {
            return erasedReference
        }

        val classDecl = decl as? KSClassDeclaration
            ?: return erasedReference

        val nested = nestedSimpleNames(classDecl)
        if (nested.isEmpty()) {
            return erasedReference
        }

        val ref = qualifiedReference(classDecl.packageName.asString(), nested)

        val typeArgs = type.arguments
        val argsStr = if (typeArgs.isEmpty()) {
            ""
        } else {
            typeArgs.joinToString(", ", "<", ">") { ksArg ->
                if (ksArg.variance == Variance.STAR || ksArg.type == null) {
                    "*"
                } else {
                    val resolved = ksArg.type!!.resolve()
                    val rendered = renderType(resolved)
                    when (ksArg.variance) {
                        Variance.COVARIANT -> "out $rendered"
                        Variance.CONTRAVARIANT -> "in $rendered"
                        else -> rendered
                    }
                }
            }
        }

        return "$ref$argsStr$nullableSuffix"
    }


    private fun render(moduleFqn: String, classes: List<ReflectClass>): String {
        val outputPkg = moduleFqn.substringBeforeLast('.', missingDelimiterValue = "")
        val outputSimple = moduleFqn.substringAfterLast('.')

        val registrations = classes.joinToString("\n\n") { c -> renderRegistration(c) }

        val body = if (classes.isEmpty()) "" else "\n$registrations\n"

        return buildString {
            append("// **DO NOT EDIT, CHANGES WILL BE LOST** - automatically generated by ReflectSymbolProcessor (KSP)\n")
            if (outputPkg.isNotEmpty()) {
                append("package $outputPkg\n")
            }
            append("\n\n")
            append("@Suppress(\"UNCHECKED_CAST\", \"KotlinRedundantDiagnosticSuppress\")\n")
            append("object $outputSimple: $MODULE_REFLECTION_FQN {\n")
            append("    override fun register(reflectionRegistry: $REFLECTION_REGISTRY_FQN) {")
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

                val serviceArgs = c.arguments.filter { it.serviceTypeQualifiedName != null }

                val lines = mutableListOf<String>()
                lines.add("reflectionRegistry.put(")
                lines.add("    $classNameLit,")
                if (serviceArgs.isEmpty()) {
                    lines.add("    listOf($argNamesList)")
                }
                else {
                    lines.add("    listOf($argNamesList),")
                    val serviceEntries = serviceArgs.joinToString(", ") {
                        "\"${it.name}\" to \"${escapeKotlinStringLiteral(it.serviceTypeQualifiedName!!)}\""
                    }
                    lines.add("    mapOf($serviceEntries)")
                }
                lines.add(") { args ->")
                lines.add("    ${c.kotlinReference}($argsCast)")
                lines.add("}")
                lines.joinToString("\n")
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
        val arguments: List<ReflectArg>
    )


    private data class ReflectArg(
        val name: String,
        val typeExpr: String,
        val serviceTypeQualifiedName: String?
    )


    companion object {
        private const val REFLECT_ANNOTATION_FQN = "tech.kzen.lib.common.reflect.Reflect"
        private const val SERVICE_ANNOTATION_FQN = "tech.kzen.lib.common.reflect.Service"
        private const val MODULE_REFLECTION_FQN = "tech.kzen.lib.common.reflect.ModuleReflection"
        private const val REFLECTION_REGISTRY_FQN = "tech.kzen.lib.common.reflect.ReflectionRegistry"
    }
}
