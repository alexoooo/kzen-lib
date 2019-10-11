package tech.kzen.lib.platform

import tech.kzen.lib.common.util.ImmutableByteArray
import java.io.ByteArrayInputStream
import java.io.InputStream


fun ImmutableByteArray.toInputStream(): InputStream {
    return ByteArrayInputStream(bytes)
}