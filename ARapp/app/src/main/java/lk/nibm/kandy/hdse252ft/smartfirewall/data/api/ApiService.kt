package lk.nibm.kandy.hdse252ft.smartfirewall.data.api

import lk.nibm.kandy.hdse252ft.smartfirewall.data.model.ThreatEvent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ── API Response Models ────────────────────────────────────────
data class ThreatResponse(
    val id: Int,
    val timestamp: String,
    val node_id: String,
    val attack_type: String,
    val severity: String,
    val source_ip: String,
    val on_chain: Boolean
)

data class StatSummary(
    val High: Int,
    val Medium: Int,
    val Low: Int,
    val total: Int
)

data class StatsResponse(
    val all_time: StatSummary,
    val weekly: StatSummary
)

data class NodeResponse(
    val id: String,
    val ip: String,
    val status: String
)

data class AuthRequest(
    val username: String,
    val password: String
)

data class AuthResponse(
    val success: Boolean,
    val message: String
)

// ── Retrofit Interface ─────────────────────────────────────────
interface FirewallApi {
    @GET("threats")
    suspend fun getThreats(): List<ThreatResponse>

    @GET("stats")
    suspend fun getStats(): StatsResponse

    @GET("nodes")
    suspend fun getNodes(): List<NodeResponse>

    @POST("register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @POST("login")
    suspend fun login(@Body request: AuthRequest): AuthResponse
}

// ── Singleton ──────────────────────────────────────────────────

object ApiClient {
    private var baseUrl = "https://192.168.1.4:8000/"

    var api: FirewallApi = createApi(baseUrl)
        private set

    fun updateBaseUrl(newIp: String) {
        val cleanIp = newIp.trim()
        val formattedUrl = when {
            cleanIp.startsWith("http://") || cleanIp.startsWith("https://") -> {
                if (cleanIp.endsWith("/")) cleanIp else "$cleanIp/"
            }
            else -> "https://$cleanIp:8000/"
        }
        baseUrl = formattedUrl
        api = createApi(baseUrl)
    }

    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            val builder = OkHttpClient.Builder()
            builder.sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
            return builder.build()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun createApi(url: String): FirewallApi {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FirewallApi::class.java)
    }
}