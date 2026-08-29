package tech.kzen.lib.common.exec.data.shape


sealed interface ShapeStability {
    data object Stable: ShapeStability
    data class Provisional(val coverage: SampleCoverage): ShapeStability
}
