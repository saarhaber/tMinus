package com.mbta.tid.mbta_app.model.response

public sealed class ApiResult<out T : Any> {
    public data class Ok<T : Any>(val data: T) : ApiResult<T>()

    public data class Error<T : Any>(val code: Int? = null, val message: String) : ApiResult<T>()

    internal companion object {
        inline fun <T : Any> runCatching(block: () -> T): ApiResult<T> {
            return try {
                Ok(block())
            } catch (e: Throwable) {
                Error(code = null, message = e.message ?: e.toString())
            }
        }
    }
}
