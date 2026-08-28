package eu.kanade.tachiyomi.network

/**
 * Exception that handles HTTP codes considered not successful by OkHttp.
 * Use it to have a standardized error message in the app across the extensions.
 *
 * @since extensions-lib 1.5
 * @param code [Int] the HTTP status code
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")

val HttpException.isAuthError: Boolean get() = code == 401 || code == 403
val HttpException.isServerError: Boolean get() = code in 500..599
