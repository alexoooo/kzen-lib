package tech.kzen.lib.common.model.structure.metadata.tag

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible
import tech.kzen.lib.platform.collect.PersistentSet
import tech.kzen.lib.platform.collect.persistentSetOf
import tech.kzen.lib.platform.collect.toPersistentSet


data class ObjectTagSet(
    val values: PersistentSet<ObjectTag>
):
    Digestible
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = ObjectTagSet(persistentSetOf())


        fun of(vararg tags: ObjectTag): ObjectTagSet {
            if (tags.isEmpty()) {
                return empty
            }
            return ObjectTagSet(persistentSetOf(*tags))
        }


        fun of(tags: Collection<ObjectTag>): ObjectTagSet {
            if (tags.isEmpty()) {
                return empty
            }
            return ObjectTagSet(tags.toPersistentSet())
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isEmpty(): Boolean {
        return values.isEmpty()
    }


    fun contains(tag: ObjectTag): Boolean {
        return tag in values
    }


    fun union(other: ObjectTagSet): ObjectTagSet {
        if (other.isEmpty()) {
            return this
        }
        if (isEmpty()) {
            return other
        }
        return ObjectTagSet(values.addAll(other.values))
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addDigestibleUnorderedSet(values)
    }
}
