package tech.kzen.lib.common.util



class Cache<T>(
        size: Int
) {
    //-----------------------------------------------------------------------------------------------------------------
    private val values = mutableMapOf<Digest, T>()


    //-----------------------------------------------------------------------------------------------------------------
    var size: Int = size
        set (value) {
            field = value
            reduceToSize()
        }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(digest: Digest): T? {
        return values[digest]
    }



    //-----------------------------------------------------------------------------------------------------------------
    fun put(digest: Digest, value: T) {
        values[digest] = value
        reduceToSize()
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun reduceToSize() {
        val iterator = values.entries.iterator()
        while (values.size >= size) {
            iterator.remove()
        }
    }
}