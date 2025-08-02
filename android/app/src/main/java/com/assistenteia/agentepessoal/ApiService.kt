package com.assistenteia.agentepessoal

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import javax.security.auth.Subject

data class AudioResponse(
    @SerializedName("audio_url")
    val audioUrl: String,
    @SerializedName("summary_text_for_debug")
    val summaryText: String
)

data class EmailRequest(
    @SerializedName("emails")
    val emails: List<EmailData>
)

data class EmailData(
    @SerializedName("from")
    val from: String?,
    @SerializedName("subject")
    val subject: String?,
    @SerializedName("snippet")
    val snippet: String?
)

interface AiApiService {
    @POST("/generate-summary-audio")
    suspend fun getSummaryAudio(@Body requestBody: EmailRequest): AudioResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://agente-pessoal-api-service-123411729400.southamerica-east1.run.app/"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val aiApiService: AiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }
}