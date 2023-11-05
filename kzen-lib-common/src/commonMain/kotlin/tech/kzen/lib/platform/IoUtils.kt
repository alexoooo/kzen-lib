package tech.kzen.lib.platform


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object IoUtils {
    fun utf8Encode(utf8: String): ByteArray
    fun utf8Decode(bytes: ByteArray): String

    fun base64Encode(bytes: ByteArray): String
    fun base64Decode(base64: String): ByteArray
}