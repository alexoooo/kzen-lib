package tech.kzen.lib.server.reflect

import org.slf4j.LoggerFactory
import tech.kzen.lib.common.reflect.ClassMirror
import tech.kzen.lib.common.reflect.GlobalMirror
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.ReflectionRegistry
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.platform.ClassName
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.primaryConstructor


/**
 * Serves [Reflect]-annotated classes that have no KSP-generated registration, via kotlin-reflect.
 * Hosts append it to the [GlobalMirror] chain, where [ReflectionRegistry.global] is seeded first and
 * always wins — so this only ever sees genuine misses, and it logs every class it serves (JS has no
 * runtime net, so a log line means codegen is missing for a class that JS could not instantiate).
 *
 * Parity with the generated registrations is the contract: primary-constructor parameters in
 * declaration order, all-positional construction with no default-argument support, Kotlin `object`s
 * served as their singleton, and [Service] parameters keyed by dotted qualified name.
 *
 * A class not annotated [Reflect] is not served at all — an unregistered, unannotated class still
 * fails fast at the graph layer.
 *
 * One instance per [ClassLoader]: a host with plugin loaders registers one mirror per loader.
 */
class ReflectiveClassMirror(
    private val classLoader: ClassLoader = ReflectiveClassMirror::class.java.classLoader
): ClassMirror {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val global = ReflectiveClassMirror()

        private val logger = LoggerFactory.getLogger(ReflectiveClassMirror::class.java)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private sealed interface Entry {
        class Instantiable(
            val constructorArgumentNames: List<String>,
            val serviceArguments: Map<String, ClassName>,
            val factory: (List<Any?>) -> Any
        ): Entry


        /** Loadable and annotated, but not instantiable — [contains] is true so [reason] surfaces. */
        class Malformed(val reason: String): Entry


        /** Not loadable, or loadable but not annotated — the gate. */
        data object NotServed: Entry
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Negatives are cached too: GlobalMirror consults its delegates on every define and create cycle
    private val entries = mutableMapOf<ClassName, Entry>()


    //-----------------------------------------------------------------------------------------------------------------
    override fun contains(className: ClassName): Boolean {
        return resolve(className) != Entry.NotServed
    }


    override fun constructorArgumentNames(className: ClassName): List<String> {
        return instantiable(className).constructorArgumentNames
    }


    override fun serviceArguments(className: ClassName): Map<String, ClassName> {
        return instantiable(className).serviceArguments
    }


    override fun create(className: ClassName, constructorArguments: List<Any?>): Any {
        return instantiable(className).factory(constructorArguments)
    }


    private fun instantiable(className: ClassName): Entry.Instantiable {
        return when (val entry = resolve(className)) {
            is Entry.Instantiable -> entry
            is Entry.Malformed -> throw IllegalArgumentException("$className: ${entry.reason}")
            Entry.NotServed -> throw IllegalArgumentException("Not found: $className")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun resolve(className: ClassName): Entry {
        synchronized(entries) { entries[className] }
            ?.let { return it }

        // Introspection initializes user classes (an `object`'s singleton), so it runs outside the monitor
        val introspected = introspect(className)

        return synchronized(entries) {
            val concurrent = entries[className]
            if (concurrent != null) {
                concurrent
            }
            else {
                entries[className] = introspected
                log(className, introspected)
                introspected
            }
        }
    }


    private fun introspect(className: ClassName): Entry {
        // The registry name convention (dotted package, '$'-joined nested names) is the JVM binary name
        val javaClass =
            try {
                Class.forName(className.get(), false, classLoader)
            }
            catch (_: ClassNotFoundException) {
                return Entry.NotServed
            }
            catch (_: LinkageError) {
                return Entry.NotServed
            }

        if (!javaClass.isAnnotationPresent(Reflect::class.java)) {
            return Entry.NotServed
        }

        val kotlinClass = javaClass.kotlin

        val objectInstance = kotlinClass.objectInstance
        if (objectInstance != null) {
            return Entry.Instantiable(listOf(), mapOf()) { objectInstance }
        }

        // A Java class has no primary constructor in kotlin-reflect
        val constructor = kotlinClass.primaryConstructor
            ?: kotlinClass.constructors.singleOrNull()
            ?: return Entry.Malformed(
                "no primary constructor, and ${kotlinClass.constructors.size} constructors to choose from")

        val constructorArgumentNames = mutableListOf<String>()
        val serviceArguments = mutableMapOf<String, ClassName>()

        for (parameter in constructor.parameters) {
            val name = parameter.name
                ?: return Entry.Malformed(
                    "constructor parameter names are unavailable" +
                            " — a Java class must be compiled with 'javac -parameters'")
            constructorArgumentNames.add(name)

            val isService = parameter.annotations.any { it.annotationClass == Service::class }
            if (!isService) {
                continue
            }

            val serviceQualifiedName = (parameter.type.classifier as? KClass<*>)?.qualifiedName
                ?: return Entry.Malformed("@Service parameter '$name' has no qualified type name")
            serviceArguments[name] = ClassName(serviceQualifiedName)
        }

        return Entry.Instantiable(constructorArgumentNames, serviceArguments) { constructorArguments ->
            call(constructor, constructorArguments)
        }
    }


    private fun call(constructor: KFunction<Any>, constructorArguments: List<Any?>): Any {
        return try {
            constructor.call(*constructorArguments.toTypedArray())
        }
        catch (e: InvocationTargetException) {
            // Generated registrations invoke the constructor directly, so its own failure propagates
            throw e.cause ?: e
        }
    }


    private fun log(className: ClassName, entry: Entry) {
        when (entry) {
            is Entry.Instantiable ->
                logger.info("Serving {} by JVM reflection — a generated registration is required on JS", className)

            is Entry.Malformed ->
                logger.warn("Annotated @Reflect but not instantiable: {} — {}", className, entry.reason)

            Entry.NotServed ->
                logger.debug("Not served by JVM reflection: {}", className)
        }
    }
}
