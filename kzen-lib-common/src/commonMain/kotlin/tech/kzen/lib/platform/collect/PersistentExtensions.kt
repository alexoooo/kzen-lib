package tech.kzen.lib.platform.collect


//---------------------------------------------------------------------------------------------------------------------
private val emptyList = PersistentList<Any>()


fun <E> persistentListOf(
        vararg elements: E
): PersistentList<E> {
    @Suppress("UNCHECKED_CAST")
    return when (elements.size) {
        0 -> emptyList as PersistentList<E>
        1 -> (emptyList as PersistentList<E>).add(elements[0])
        else -> (emptyList as PersistentList<E>).addAll(elements.asList())
    }
}


fun <E> Iterable<E>.toPersistentList(): PersistentList<E> {
    if (this is PersistentList) {
        return this
    }
    return PersistentList<E>().addAll(this)
}


//---------------------------------------------------------------------------------------------------------------------
private val emptyMap = PersistentMap<Any, Any>()


fun <K, V> persistentMapOf(vararg pairs: Pair<K, V>): PersistentMap<K, V> {
    @Suppress("UNCHECKED_CAST")
    return when (pairs.size) {
        0 -> emptyMap as PersistentMap<K, V>
        1 -> (emptyMap as PersistentMap<K, V>).put(pairs[0].first, pairs[0].second)
        else -> {
            var builder = emptyMap as PersistentMap<K, V>
            for ((k, v) in pairs) {
                builder = builder.put(k, v)
            }
            builder
        }
    }
}


fun <K, V> Map<K, V>.toPersistentMap(): PersistentMap<K, V> {
    if (this is PersistentMap) {
        return this
    }

    var builder = PersistentMap<K, V>()
    forEach {
        builder = builder.put(it.key, it.value)
    }
    return builder
}


fun <K, V> Iterable<Pair<K, V>>.toPersistentMap(): PersistentMap<K, V> {
    return toMap().toPersistentMap()
}


//---------------------------------------------------------------------------------------------------------------------
private val emptySet = PersistentSet<Any>()


fun <E> persistentSetOf(
    vararg elements: E
): PersistentSet<E> {
    @Suppress("UNCHECKED_CAST")
    return when (elements.size) {
        0 -> emptySet as PersistentSet<E>
        1 -> (emptySet as PersistentSet<E>).add(elements[0])
        else -> PersistentSet<E>().addAll(elements.asList())
    }
}


fun <T> Iterable<T>.toPersistentSet(): PersistentSet<T> {
    if (this is PersistentSet) {
        return this
    }
    return PersistentSet<T>().addAll(this)
}