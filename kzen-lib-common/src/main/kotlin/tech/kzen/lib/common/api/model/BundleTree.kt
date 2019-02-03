package tech.kzen.lib.common.api.model


data class BundleTree<T>(
        val values: Map<BundlePath, T>
) {
    fun get(bundlePath: BundlePath): T {
        return values[bundlePath]
                ?: throw IllegalArgumentException("Missing: $bundlePath")
    }

    override fun toString(): String {
        return values.toString()
    }
}