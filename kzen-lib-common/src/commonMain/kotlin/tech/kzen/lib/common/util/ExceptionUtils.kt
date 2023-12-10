package tech.kzen.lib.common.util


object ExceptionUtils {
    fun message(throwable: Throwable): String {
        val errorName = throwable::class
            .simpleName
            ?.removeSuffix("Exception")
            ?.replace(Regex("([A-Z])"), " $1")
            ?.trim()

        val message = throwable.message

        val fullMessage =
            if (errorName != null) {
                if (message != null) {
                    "$errorName: $message"
                }
                else {
                    errorName
                }
            }
            else {
                message ?: "exception"
            }

        return fullMessage
    }
}