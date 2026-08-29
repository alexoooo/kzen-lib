package tech.kzen.lib.common.exec.data.type


class NativeTypeResolutionScope(
    describers: List<NativeTypeDescriber> = emptyList()
): AutoCloseable {
    val resolver: NativeTypeResolver = DefaultNativeTypeResolver(describers)

    override fun close() {
        resolver.close()
    }
}
