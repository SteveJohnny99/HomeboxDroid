package it.stevej.homeboxdroid.data

import retrofit2.HttpException
import java.io.IOException

/** Esito di una chiamata al repository. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

/** Esegue una chiamata di rete mappando le eccezioni in ApiResult.Error leggibili. */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        ApiResult.Error(mapHttpError(e.code()), e.code())
    } catch (e: IOException) {
        ApiResult.Error("Server non raggiungibile. Controlla URL, rete o VPN.")
    } catch (e: Exception) {
        ApiResult.Error("Errore imprevisto: ${e.message ?: e.javaClass.simpleName}")
    }

fun mapHttpError(code: Int): String = when (code) {
    401 -> "Autenticazione fallita: credenziale non valida o scaduta."
    403 -> "Accesso negato (403). Verifica il bypass Authelia su /api."
    404 -> "Endpoint non trovato (404). Controlla URL e versione Homebox."
    422 -> "Dati non validi (422)."
    in 500..599 -> "Errore del server ($code)."
    else -> "Errore di rete ($code)."
}
