package it.stevej.homeboxdroid.data.remote

import it.stevej.homeboxdroid.data.SecureSettings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Riscrive scheme/host/porta (ed eventuale sotto-path) di ogni richiesta in base
 * all'URL dell'istanza configurata. Permette una sola istanza Retrofit per
 * qualunque server self-hosted.
 */
class BaseUrlInterceptor(private val settings: SecureSettings) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val configured = settings.serverUrl?.trim()?.toHttpUrlOrNull()
            ?: return chain.proceed(request)

        val builder = configured.newBuilder()
        // I segmenti della richiesta placeholder (api/v1/...) vengono appesi
        // dopo l'eventuale base-path dell'istanza.
        request.url.encodedPathSegments.forEach { seg ->
            if (seg.isNotEmpty()) builder.addEncodedPathSegment(seg)
        }
        builder.encodedQuery(request.url.encodedQuery)

        val newUrl = builder.build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

/**
 * Aggiunge "Authorization: Bearer <credenziale>" quando presente.
 * La stessa logica vale sia per il token di login sia per l'API Key hb_.
 */
class AuthInterceptor(private val settings: SecureSettings) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cred = settings.credential
        if (cred.isNullOrBlank() || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        val authed = request.newBuilder()
            .header("Authorization", "Bearer $cred")
            .build()
        return chain.proceed(authed)
    }
}

/**
 * TrustManager che rispetta il flag live allowSelfSigned:
 * - flag OFF: delega al trust manager di sistema (verifica normale);
 * - flag ON: accetta anche certificati non attendibili (solo opt-in utente).
 * Non disabilita MAI la verifica in modo globale/silenzioso.
 */
object TlsFactory {

    fun socketFactory(settings: SecureSettings): Pair<SSLSocketFactory, X509TrustManager> {
        val tm = DynamicTrustManager(settings)
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf(tm), java.security.SecureRandom())
        return ctx.socketFactory to tm
    }

    fun hostnameVerifier(settings: SecureSettings): HostnameVerifier =
        HostnameVerifier { hostname, session ->
            if (settings.allowSelfSigned) true
            else DEFAULT.verify(hostname, session)
        }

    private val DEFAULT: HostnameVerifier =
        javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()

    private class DynamicTrustManager(
        private val settings: SecureSettings,
    ) : X509TrustManager {
        private val system: X509TrustManager = systemTrustManager()

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (!settings.allowSelfSigned) system.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (!settings.allowSelfSigned) system.checkServerTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
    }

    private fun systemTrustManager(): X509TrustManager {
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        )
        tmf.init(null as java.security.KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
