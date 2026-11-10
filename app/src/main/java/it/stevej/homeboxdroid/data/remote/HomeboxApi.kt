package it.stevej.homeboxdroid.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Interfaccia Retrofit per l'API Homebox v0.26.x.
 * La base URL e' un placeholder: BaseUrlInterceptor riscrive scheme/host/porta
 * in base all'istanza configurata dall'utente. I path includono gia' /api.
 */
interface HomeboxApi {

    @GET("v1/status")
    suspend fun status(): StatusDto

    @POST("v1/users/login")
    suspend fun login(@Body body: LoginFormDto): TokenResponseDto

    @GET("v1/users/refresh")
    suspend fun refresh(): TokenResponseDto

    @POST("v1/users/logout")
    suspend fun logout()

    @GET("v1/users/self")
    suspend fun self(): WrappedUserDto

    // Entities
    @GET("v1/entities")
    suspend fun entities(
        @Query("q") q: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("parentIds") parentIds: List<String>? = null,
        @Query("entityTypeIds") entityTypeIds: List<String>? = null,
        @Query("tags") tags: List<String>? = null,
        @Query("matchAllTags") matchAllTags: Boolean? = null,
        @Query("orderBy") orderBy: String? = null,
    ): EntityListResultDto

    @GET("v1/entities/tree")
    suspend fun tree(@Query("withItems") withItems: Boolean = true): List<TreeItemDto>

    @GET("v1/entities/{id}")
    suspend fun entity(@Path("id") id: String): EntityOutDto

    @POST("v1/entities")
    suspend fun createEntity(@Body body: EntityCreateDto): EntityOutDto

    @PUT("v1/entities/{id}")
    suspend fun updateEntity(@Path("id") id: String, @Body body: EntityUpdateDto): EntityOutDto

    @DELETE("v1/entities/{id}")
    suspend fun deleteEntity(@Path("id") id: String)

    // Entity types
    @GET("v1/entity-types")
    suspend fun entityTypes(): List<EntityTypeSummaryDto>

    @POST("v1/entity-types")
    suspend fun createEntityType(@Body body: EntityTypeCreateDto): EntityTypeSummaryDto

    // Tags
    @GET("v1/tags")
    suspend fun tags(): List<TagSummaryDto>

    @POST("v1/tags")
    suspend fun createTag(@Body body: TagCreateDto): TagSummaryDto

    // Allegati
    @Multipart
    @POST("v1/entities/{id}/attachments")
    suspend fun uploadAttachment(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part("name") name: RequestBody,
        @Part("type") type: RequestBody,
        @Part("primary") primary: RequestBody,
    ): EntityOutDto

    @DELETE("v1/entities/{id}/attachments/{attachmentId}")
    suspend fun deleteAttachment(
        @Path("id") id: String,
        @Path("attachmentId") attachmentId: String,
    )

    // Ricerca prodotto da barcode (EAN/UPC).
    // NB: il backend decodifica il parametro come "productEAN" (lo swagger dice
    // erroneamente "data"); con "data" il server va in errore. 204 = nessun
    // risultato (corpo vuoto), quindi il tipo di ritorno e' nullable.
    @GET("v1/products/search-from-barcode")
    suspend fun searchBarcode(@Query("productEAN") productEAN: String): List<BarcodeProductDto>?

    // Statistiche
    @GET("v1/groups/statistics")
    suspend fun statistics(): GroupStatisticsDto
}
