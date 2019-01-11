package tech.kzen.lib.common.api.model

import tech.kzen.lib.common.notation.model.BundleNotation


data class BundleTree<T>(
        val values: Map<BundlePath, T>
)