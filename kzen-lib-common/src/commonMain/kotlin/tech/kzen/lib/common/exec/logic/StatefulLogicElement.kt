package tech.kzen.lib.common.exec.logic


interface StatefulLogicElement<in T> {
    fun loadState(previous: T)
}
