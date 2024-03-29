package tech.kzen.lib.common.exec

import tech.kzen.lib.common.util.ExceptionUtils
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


@Suppress("ConstPropertyName")
sealed class ExecutionResult
    : Digestible
{
    companion object {
        const val errorKey = "error"
        const val valueKey = "value"
        const val detailKey = "detail"

        fun fromJsonCollection(collection: Map<String, Any?>): ExecutionResult {
            val error = collection[errorKey]

            return if (error == null) {
                ExecutionSuccess.fromJsonCollection(collection)
            }
            else {
                ExecutionFailure(error as String)
            }
        }


        fun success(
            value: ExecutionValue = NullExecutionValue,
            detail: ExecutionValue = NullExecutionValue
        ): ExecutionSuccess {
            return ExecutionSuccess(value, detail)
        }


        fun failure(
            message: String
        ): ExecutionFailure {
            return ExecutionFailure(message)
        }
    }


    abstract fun toJsonCollection(): Map<String, Any?>


    override fun digest(): Digest {
        val digest = Digest.Builder()
        digest(digest)
        return digest.digest()
    }


    override fun digest(sink: Digest.Sink) {
        when (this) {
            is ExecutionFailure -> {
                sink.addBoolean(false)
                sink.addUtf8(errorMessage)
            }

            is ExecutionSuccess -> {
                sink.addBoolean(true)
                sink.addDigestible(value)
                sink.addDigestible(detail)
            }
        }
    }
}


data class ExecutionFailure(
    val errorMessage: String
): ExecutionResult() {
    companion object {
        fun ofException(throwable: Throwable): ExecutionFailure {
            return ofException("", throwable)
        }

        @Suppress("MemberVisibilityCanBePrivate")
        fun ofException(userMessage: String, throwable: Throwable): ExecutionFailure {
            val errorMessage = ExceptionUtils.message(throwable)
            return ExecutionFailure(userMessage + errorMessage)
        }
    }


    override fun toJsonCollection(): Map<String, Any?> {
        return mapOf(
            errorKey to errorMessage
        )
    }
}


data class ExecutionSuccess(
    val value: ExecutionValue,
    val detail: ExecutionValue
): ExecutionResult() {
    companion object {
        val empty = ExecutionSuccess(NullExecutionValue, NullExecutionValue)

        fun ofValue(value: ExecutionValue): ExecutionSuccess {
            return ExecutionSuccess(value, detail = NullExecutionValue)
        }


        @Suppress("UNCHECKED_CAST")
        fun fromJsonCollection(collection: Map<String, Any?>): ExecutionSuccess {
            return ExecutionSuccess(
                ExecutionValue.fromJsonCollection(collection[valueKey] as Map<String, Any>),
                ExecutionValue.fromJsonCollection(collection[detailKey] as Map<String, Any>)
            )
        }
    }


    fun withDetail(detail: ExecutionValue): ExecutionSuccess {
        return ExecutionSuccess(value, detail)
    }


    override fun toJsonCollection(): Map<String, Any?> {
        return mapOf(
            valueKey to value.toJsonCollection(),
            detailKey to detail.toJsonCollection()
        )
    }


    override fun digest(): Digest {
        val digest = Digest.Builder()
        value.digest(digest)
        detail.digest(digest)
        return digest.digest()
    }
}
