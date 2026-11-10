package it.stevej.homeboxdroid.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import dagger.hilt.android.qualifiers.ApplicationContext
import it.stevej.homeboxdroid.data.SecureSettings
import it.stevej.homeboxdroid.data.remote.AuthInterceptor
import it.stevej.homeboxdroid.data.remote.BaseUrlInterceptor
import it.stevej.homeboxdroid.data.remote.HomeboxApi
import it.stevej.homeboxdroid.data.remote.KotlinxJsonConverterFactory
import it.stevej.homeboxdroid.data.remote.TlsFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Provider a livello applicazione: Json, OkHttp (con interceptor auth/base-url
 * e gestione TLS self-signed opt-in), Retrofit e l'API Homebox.
 * SecureSettings e HomeboxRepository sono @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Base placeholder: BaseUrlInterceptor riscrive host/porta a runtime.
    private const val PLACEHOLDER_BASE = "http://localhost/api/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(settings: SecureSettings): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            // Non loggare mai le credenziali.
            redactHeader("Authorization")
        }
        val (sslFactory, trustManager) = TlsFactory.socketFactory(settings)
        return OkHttpClient.Builder()
            .addInterceptor(BaseUrlInterceptor(settings))
            .addInterceptor(AuthInterceptor(settings))
            .addInterceptor(logging)
            .sslSocketFactory(sslFactory, trustManager)
            .hostnameVerifier(TlsFactory.hostnameVerifier(settings))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // Upload di foto/allegati: la scrittura puo' richiedere piu' tempo
            // su reti lente o VPN, evita timeout spuri.
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(KotlinxJsonConverterFactory(json, contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): HomeboxApi = retrofit.create(HomeboxApi::class.java)

    /**
     * ImageLoader di Coil per gli allegati: client dedicato con auth e TLS ma
     * SENZA riscrittura base-url (le immagini usano URL assoluti verso l'istanza).
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        settings: SecureSettings,
    ): ImageLoader {
        val (sslFactory, trustManager) = TlsFactory.socketFactory(settings)
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(settings))
            .sslSocketFactory(sslFactory, trustManager)
            .hostnameVerifier(TlsFactory.hostnameVerifier(settings))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(context)
            .okHttpClient(client)
            .diskCache(
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            )
            .crossfade(true)
            .build()
    }
}
