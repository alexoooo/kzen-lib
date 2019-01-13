package tech.kzen.lib.common.api.model


data class BundleTree<T>(
        val values: Map<BundlePath, T>
) {
    override fun toString(): String {
        return values.toString()
    }
}