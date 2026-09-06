package tech.kzen.lib.server.reflect

import java.io.IOException
import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap


/**
 * One delegating loader over a set of named, parent-first plugin scope loaders that share [parent]. It never
 * defines a class itself: the parent is consulted first (the application classpath wins), and on a miss each
 * scope is asked whether it *defines* the name (its own jars, via [URLClassLoader.findResource]) — exactly one
 * defining scope returns that scope's existing `Class`, more than one is an [AmbiguousClassException]. A folder
 * class the parent also defines is served as the parent's copy and reported once to [diagnostics] as shadowed.
 *
 * Because every scope loader is itself parent-first over the same parent, a value a scope's mirror constructed
 * and a type a compiled expression names through this loader are the same `Class`.
 */
class AggregateClassLoader(
    parent: ClassLoader,
    private val scopes: List<Scope>,
    private val diagnostics: Diagnostics = Diagnostics.none
): ClassLoader("kzen-aggregate", parent) {
    /** A plugin scope: its id and its own (parent-first) URL loader. */
    class Scope(
        val id: String,
        val loader: URLClassLoader
    )


    interface Diagnostics {
        /** [className] exists on the application classpath and in [scopeId]'s jars; the application copy is served. */
        fun shadowed(scopeId: String, className: String)

        /** [className] is defined by every scope in [scopeIds]; resolution failed by name. */
        fun ambiguous(scopeIds: List<String>, className: String)

        companion object {
            val none = object: Diagnostics {
                override fun shadowed(scopeId: String, className: String) {}
                override fun ambiguous(scopeIds: List<String>, className: String) {}
            }
        }
    }


    companion object {
        private const val classFileSuffix = ".class"

        init {
            registerAsParallelCapable()
        }

        private fun classResource(name: String): String {
            return name.replace('.', '/') + classFileSuffix
        }
    }


    private val reportedShadows: MutableSet<String> = ConcurrentHashMap.newKeySet()


    fun scopeIds(): List<String> {
        return scopes.map { it.id }
    }


    /** The scopes whose own jars carry [name], without loading it. */
    fun definingScopes(name: String): List<Scope> {
        val resource = classResource(name)
        return scopes.filter { it.loader.findResource(resource) != null }
    }


    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            val fromParent = try {
                parent.loadClass(name)
            }
            catch (_: ClassNotFoundException) {
                null
            }
            if (fromParent != null) {
                reportShadow(name)
                if (resolve) {
                    resolveClass(fromParent)
                }
                return fromParent
            }
            val loaded = findClass(name)
            if (resolve) {
                resolveClass(loaded)
            }
            return loaded
        }
    }


    override fun findClass(name: String): Class<*> {
        val defining = definingScopes(name)
        return when (defining.size) {
            0 -> throw ClassNotFoundException(name)
            1 -> defining.single().loader.loadClass(name)
            else -> {
                val ids = defining.map { it.id }
                diagnostics.ambiguous(ids, name)
                throw AmbiguousClassException(name, ids)
            }
        }
    }


    private fun reportShadow(name: String) {
        if (reportedShadows.contains(name)) {
            return
        }
        val defining = definingScopes(name)
        if (defining.isNotEmpty() && reportedShadows.add(name)) {
            for (scope in defining) {
                diagnostics.shadowed(scope.id, name)
            }
        }
    }


    override fun findResource(name: String): URL? {
        for (scope in scopes) {
            scope.loader.findResource(name)?.let { return it }
        }
        return null
    }


    @Throws(IOException::class)
    override fun findResources(name: String): Enumeration<URL> {
        val urls = mutableListOf<URL>()
        for (scope in scopes) {
            urls.addAll(Collections.list(scope.loader.findResources(name)))
        }
        return Collections.enumeration(urls)
    }
}
