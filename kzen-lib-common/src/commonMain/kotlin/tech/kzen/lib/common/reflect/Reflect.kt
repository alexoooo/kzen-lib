package tech.kzen.lib.common.reflect


/**
 * Marks a class (or Kotlin `object`, including companion objects) as instantiable by the kzen graph
 * layer through the cross-platform reflection registry. The KSP processor (`kzen-lib-reflect-ksp`)
 * scans each consuming Gradle module for `@Reflect` classes and generates one [ModuleReflection]
 * object per module (FQN set by the module's `kzen.reflect.moduleClassName` KSP arg) whose
 * `register()` records, per class: the registry name, the ordered primary-constructor parameter
 * names, any [Service] parameter types (by fully qualified name), and an all-positional constructor
 * lambda.
 *
 * The contract:
 *
 * - **Primary constructor only, all-positional, defaults bypassed.** Instantiation supplies every
 *   parameter of the primary constructor, in declaration order — constructor default values are
 *   never used (the definition/notation layer is responsible for supplying every argument).
 *   Secondary constructors are ignored.
 * - **Type parameters erase to `Any` / `Any?`.** A use of a type parameter in a constructor
 *   parameter type renders as `kotlin.Any` (nullable: `kotlin.Any?`) in the generated cast;
 *   generics carry no runtime checking beyond that cast.
 * - **Registry name convention:** package plus the nested-class path joined with `$`
 *   (e.g. `com.example.Outer$Nested`), matching the JVM binary-name shape.
 * - **Processed source sets — sharp edge.** The processor runs only where the consuming build wires
 *   it: a multiplatform module's `commonMain` (via `kspCommonMainMetadata`) and a single-target
 *   module's main source set (via `ksp` / `kspJs` / `kspTest`). A `@Reflect` class in a KMP module's
 *   `jvmMain` or `jsMain` is **silently unprocessed** — it compiles, but is absent from the registry
 *   and fails at instantiation time. There is no Gradle-side guard; keep platform-specific
 *   `@Reflect` classes in single-target modules. On the JVM a host may install a reflective fallback
 *   mirror (see [GlobalMirror]) that serves unregistered classes at runtime, logging each hit;
 *   Kotlin/JS has no such net.
 * - **Kotlin only.** Java declarations are skipped by the processor (they have no Kotlin primary
 *   constructor); a `@Reflect` Java class is served by the JVM reflective fallback instead.
 * - **Not supported:** `inner` classes are a processor error (the generated constructor call has no
 *   outer receiver); local classes are invisible to the processor, so they register nowhere.
 *
 * See [Service] for constructor parameters supplied by the host's environment rather than resolved
 * from notation, and [ReflectionRegistry] / [ModuleReflection] for the runtime side.
 */
annotation class Reflect {
    companion object {
        val simpleName: String = Reflect::class.simpleName!!
        val qualifiedName = "tech.kzen.lib.common.reflect.$simpleName"
    }
}