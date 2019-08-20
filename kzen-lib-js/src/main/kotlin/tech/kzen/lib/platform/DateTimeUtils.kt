package tech.kzen.lib.platform

import kotlin.js.Date


actual object DateTimeUtils {
    actual fun filenameTimestamp(): String {
        val now = Date()

//        return Date()
//                .toISOString()
//                .replace("-", "")
//                .replace(":", "")
//                .replace("T", "_")
//                .replace(".", "_")
//                .replace("Z", "")

        return now.getFullYear().toString() +
                (now.getMonth() + 1).toString().padStart(2, '0') +
                now.getDate().toString().padStart(2, '0') +
                "_" +
                now.getHours().toString().padStart(2, '0') +
                now.getMinutes().toString().padStart(2, '0') +
                now.getSeconds().toString().padStart(2, '0') +
                "_" +
                now.getMilliseconds().toString().padStart(3, '0')
    }
}