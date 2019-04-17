package tech.kzen.lib.platform.collect


//---------------------------------------------------------------------------------------------------------------------
fun <T> persistentListOf(
        vararg elements: T
): PersistentList<T> {
    var builder = PersistentList<T>()
    for (e in elements) {
        builder = builder.add(e)
    }
    return builder
}


fun <T> List<T>.toPersistentList(): PersistentList<T> {
    var builder = PersistentList<T>()
    forEach {
        builder = builder.add(it)
    }
    return builder
}


//---------------------------------------------------------------------------------------------------------------------
fun <K, V> persistentMapOf(vararg pairs: Pair<K, V>): PersistentMap<K, V> {
    var builder = PersistentMap<K, V>()
    for ((k, v) in pairs) {
        builder = builder.put(k, v)
    }
    return builder
}


fun <K, V> Map<K, V>.toPersistentMap(): PersistentMap<K, V> {
    var builder = PersistentMap<K, V>()
    forEach {
        builder = builder.put(it.key, it.value)
    }
    return builder
}


fun <K, V> Iterable<Pair<K, V>>.toPersistentMap(): PersistentMap<K, V> {
    return toMap().toPersistentMap()
}
