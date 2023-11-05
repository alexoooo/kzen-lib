package tech.kzen.lib.platform

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")
actual object DateTimeUtils {
    private val filenamePattern = DateTimeFormatter.ofPattern(
            "yyyyMMdd'_'HHmmss'_'SSS")

    actual fun filenameTimestamp(): String {
        return filenamePattern.format(
                LocalDateTime.now())
    }
}