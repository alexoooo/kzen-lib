package tech.kzen.lib.platform

import java.time.*
import java.time.format.DateTimeFormatter


actual object DateTimeUtils {
    private val filenamePattern = DateTimeFormatter.ofPattern(
            "yyyyMMdd'_'HHmmss'_'SSS")

    actual fun filenameTimestamp(): String {
        return filenamePattern.format(
                LocalDateTime.now())
    }
}