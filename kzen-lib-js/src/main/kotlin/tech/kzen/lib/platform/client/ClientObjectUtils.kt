package tech.kzen.lib.platform.client


object ClientObjectUtils {
    @Suppress("UNUSED_PARAMETER")
    fun getOwnPropertyNames(any: Any): Array<String> {
        return js("Object.getOwnPropertyNames(any)") as Array<String>
    }
}