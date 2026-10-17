package tech.kzen.lib.common.exec.engine.resource


/**
 * The resources a value's lifetime depends on: its own entry when it was adopted, plus whatever the value it
 * was derived or navigated from depended on. Almost always of size one. A hold on the set is a hold on every
 * member, so a child in flight keeps its parent open. **Order is a guarantee:** members are held and released
 * in list order, and a derived value's own entries come before its inherited ones ([plus]), so one holder
 * releasing both closes the child before the parent.
 */
class ResourceOwners private constructor(
    private val members: List<Resource>
) {
    companion object {
        val empty = ResourceOwners(emptyList())

        fun of(resource: Resource): ResourceOwners =
            ResourceOwners(listOf(resource))
    }


    val isEmpty: Boolean
        get() = members.isEmpty()


    fun entries(): List<Resource> = members


    operator fun contains(resource: Resource): Boolean =
        members.any { it === resource }


    /** The member standing for [native], if any (identity). */
    fun entryOf(native: Any): Resource? =
        members.firstOrNull { it.native === native }


    /** The first member that is closed, or null when every member is open. */
    fun closed(): Resource? =
        members.firstOrNull { it.isClosed }


    /** Union by identity, keeping this set's order first. */
    operator fun plus(other: ResourceOwners?): ResourceOwners {
        if (other == null || other.isEmpty) return this
        if (isEmpty) return other
        val merged = members.toMutableList()
        for (entry in other.members) {
            if (merged.none { it === entry }) merged += entry
        }
        return ResourceOwners(merged)
    }


    /**
     * One hold per member for [holder]; releasing the returned hold releases every member's once, in member order.
     * Taken in member order and, on a member that is already closed, the holds taken so far are released again
     * and the failure propagates — a value cannot be half-held.
     */
    fun hold(holder: Holder): Hold {
        if (members.isEmpty()) {
            return Hold.none
        }
        if (members.size == 1) {
            return members[0].hold(holder)
        }
        val taken = ArrayList<Hold>(members.size)
        try {
            for (entry in members) {
                taken += entry.hold(holder)
            }
        }
        catch (e: IllegalStateException) {
            taken.forEach { it.release() }
            throw e
        }
        return CompositeHold(taken)
    }


    override fun toString(): String = "ResourceOwners($members)"
}
