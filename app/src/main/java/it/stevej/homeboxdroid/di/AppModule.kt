package it.stevej.homeboxdroid.di

import it.stevej.homeboxdroid.data.SecureSettings
import it.stevej.homeboxdroid.data.remote.AuthInterceptor
import it.stevej.homeboxdroid.data.remote.BaseUrlInterceptor
import it.stevej.homeboxdroid.data.remote.HomeboxApi
import it.stevej.homeboxdroid.data.remote.TlsFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): HomeboxApi = retrofit.create(HomeboxApi::class.java)
}
