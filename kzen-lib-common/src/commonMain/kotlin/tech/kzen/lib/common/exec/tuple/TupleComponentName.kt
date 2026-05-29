package tech.kzen.lib.common.exec.tuple


data class TupleComponentName(
    val value: String
) {
    companion object {
        val main = TupleComponentName("main")
        val detail = TupleComponentName("detail")
    }
}
