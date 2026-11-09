package it.stevej.homeboxdroid.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Converter.Factory minimale per kotlinx.serialization, scritto in casa per non
 * dipendere da librerie converter di terze parti (il cui package cambia tra
 * versioni). Usa solo retrofit + okhttp + kotlinx-serialization-json, tutti
 * gia' presenti. Gestisce anche i metodi che non restituiscono corpo (Unit).
 */
@OptIn(ExperimentalSerializationApi::class)
class KotlinxJsonConverterFactory(
    private val json: Json,
    private val contentType: MediaType,
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        if (type === Unit::class.java || type === Void::class.java) {
            return Converter<ResponseBody, Unit> { body -> body.close() }
        }
        val loader = json.serializersModule.serializer(type)
        return Converter<ResponseBody, Any?> { body ->
            val text = body.string()
            if (text.isBlank()) null else json.decodeFromString(loader, text)
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> {
        val saver = json.serializersModule.serializer(type)
        return Converter<Any?, RequestBody> { value ->
            json.encodeToString(saver, value).toRequestBody(contentType)
        }
    }
}
