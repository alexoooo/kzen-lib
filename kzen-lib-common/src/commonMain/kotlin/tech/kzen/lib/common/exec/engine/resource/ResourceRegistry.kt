package tech.kzen.lib.common.exec.engine.resource

import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.value.DataAccessException
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.identityMap
import tech.kzen.lib.platform.platformSynchronized


/**
 * One run's resources: every entry adopted in the run, indexed by native identity so adoption is idempotent —
 * a fresh [DataValue] wrapped around a native the run already owns (a lift, a boundary re-wrap, a navigated
 * child that is itself closeable) attaches the existing entry instead of adopting twice. Run state, not node
 * state: it outlives every frame and survives a live edit. Behind its OWN lock, never the engine's (holds and
 * releases are the hot path of a streaming Job); the entries' own atomics do the counting.
 *
 * **The one rule.** Taking a hold on a value whose root native is an `AutoCloseable` adopts it when the index
 * does not know it (closer = `close`) and attaches the known entry when it does; either way the returned value
 * carries [DataValue.owners]. A borrowed native ([Resource.borrowed]) is indexed too, so it is visible in
 * [live] and nobody closes it. The fast paths: a `Scalar` type is never adopted, closeability is decided once
 * per native root class, and [hold] returns null for an unowned value — no allocation.
 *
 * [onChange] is fired after every adoption, hold, release and close, so a run view can refresh its live list.
 */
class ResourceRegistry(
    private val onChange: () -> Unit = {}
) {
    /** What adopting a native yielded: its entry and the hold [adopt] took on it for the caller. */
    class Adoption(
        val resource: Resource,
        val hold: Hold
    )


    private val lock = Any()
    private val entries = identityMap<Resource>()
    // Live entries the index cannot key: anonymous teardowns and closers over natives that are not closeable
    // (a browser handle, a file named by a string); each is its own entry, visible in [live] until closed
    private val unindexed = identityMap<Resource>()
    // Per native root class, whether it is closeable at all — a scalar's boxed class is looked at once.
    private val closeableByClass = HashMap<ClassName, Boolean>()


    /**
     * The run's entry for [native] on behalf of [holder]: the existing one when the run already owns it (the
     * given [closer] is then not used — a native closes once, by the closer it was first adopted with), else a new
     * entry closed by [closer]. A null [closer] adopts a borrowed native. A null or non-closeable [native] with
     * a closer gets an unindexed entry of its own — an explicit teardown for a handle this registry cannot
     * identify (a browser that is not `AutoCloseable`, a temp file named by a string).
     */
    fun adopt(native: Any?, closer: (() -> Unit)?, holder: Holder): Adoption {
        val resource = if (native == null || native !is AutoCloseable) {
            Resource(native, closer ?: {}, ::dropClosed).also { entry ->
                platformSynchronized(lock) { unindexed[entry] = entry }
            }
        }
        else {
            indexed(native) { Resource(native, closer, ::dropClosed) }
        }
        val hold = resource.hold(holder)
        onChange()
        return Adoption(resource, hold)
    }


    /** An anonymous teardown for [holder]: an entry that stands for nothing but runs [closer] when released. */
    fun register(closer: () -> Unit, holder: Holder): Adoption =
        adopt(null, closer, holder)


    /**
     * [holder]'s hold on everything [value] depends on, with the value that carries its owners. A root native
     * that is an `AutoCloseable` the run does not know yet is adopted here (closer = `close`), and the value's
     * owners become that entry plus whatever it already carried; a known one is attached. Null when the value
     * depends on nothing owned, so an unowned transfer costs no hold and no allocation.
     */
    fun hold(value: DataValue, holder: Holder): Held? {
        val inherited = value.owners
        val native = closeableRoot(value)
        if (native != null && inherited?.entryOf(native) == null) {
            val entry = indexed(native) { Resource(native, native::close, ::dropClosed) }
            val owners = ResourceOwners.of(entry) + inherited
            val hold = owners.hold(holder)
            onChange()
            return Held(value.withOwners(owners), hold)
        }
        if (inherited == null || inherited.isEmpty) {
            return null
        }
        if (native != null) {
            // A borrowed root lifted outside this registry becomes visible here on its first hold
            inherited.entryOf(native)?.let { entry -> indexed(native) { entry } }
        }
        val hold = inherited.hold(holder)
        onChange()
        return Held(value, hold)
    }


    /** The run's entry for an owned native, or null when the run does not know it. */
    fun entryOf(native: Any): Resource? =
        platformSynchronized(lock) { entries[native] }


    /** Entries not yet closed: everything some holder can still reach. */
    fun live(): List<Resource> =
        platformSynchronized(lock) { entries.values.toList() + unindexed.values }


    /** Aggregated live holds by holder. */
    fun holdsByHolder(): Map<Holder, Int> {
        val counts = LinkedHashMap<Holder, Int>()
        for (entry in live()) {
            for ((holder, count) in entry.holds()) {
                counts[holder] = (counts[holder] ?: 0) + count
            }
        }
        return counts
    }


    /** [holder]'s live holds across every entry. */
    fun holdsOf(holder: Holder): Int =
        holdsByHolder()[holder] ?: 0


    private fun indexed(native: Any, create: () -> Resource): Resource =
        platformSynchronized(lock) {
            entries[native] ?: create().also { entries[native] = it }
        }


    // The value's root native when it is an AutoCloseable; null otherwise, decided per native class after the
    // first sight of that class so a stream of scalars never reads its boxed natives
    private fun closeableRoot(value: DataValue): AutoCloseable? {
        if (value.type is DataType.Scalar) {
            return null
        }
        if (value.access.state(value.root) != DataState.Present) {
            return null
        }
        val rootNative = value.payloadContract.nativeByPath[DataTypePath.root]
            ?: return null
        val known = platformSynchronized(lock) { closeableByClass[rootNative.className] }
        if (known == false) {
            return null
        }
        val native = try {
            value.access.native(value.root)
        }
        catch (_: DataAccessException) {
            return null
        }
        val closeable = native is AutoCloseable
        platformSynchronized(lock) { closeableByClass[rootNative.className] = closeable }
        return native as? AutoCloseable
    }


    private fun dropClosed(resource: Resource) {
        platformSynchronized(lock) {
            val native = resource.native
            if (native != null && entries[native] === resource) {
                entries.remove(native)
            }
            unindexed.remove(resource)
        }
        onChange()
    }
}
