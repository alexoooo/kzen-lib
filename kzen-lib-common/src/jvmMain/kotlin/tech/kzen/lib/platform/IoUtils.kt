package tech.kzen.lib.platform

import java.nio.charset.StandardCharsets
import java.util.*


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")
actual object IoUtils {
    actual fun utf8Decode(bytes: ByteArray): String {
        return String(bytes, StandardCharsets.UTF_8)
    }

    actual fun utf8Encode(utf8: String): ByteArray {
        return utf8.toByteArray(StandardCharsets.UTF_8)
    }


    actual fun base64Encode(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    actual fun base64Decode(base64: String): ByteArray {
        return Base64.getDecoder().decode(base64)
    }
}