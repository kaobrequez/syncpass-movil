package cl.kcorp.syncpass.data.remote

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SupabaseException(
    val status: Int,
    val code: String?,
    message: String,
) : Exception(message)

/**
 * Cliente mínimo de PostgREST/Supabase usando la anon key, equivalente al
 * cliente `@supabase/supabase-js` que usa la versión web.
 */
object SupabaseClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String {
        if (!SupabaseConfig.isConfigured) {
            throw IllegalStateException(
                "Supabase no está configurado. Agrega SUPABASE_URL y SUPABASE_ANON_KEY en local.properties.",
            )
        }
        return SupabaseConfig.url
    }

    private fun requestBuilder(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.anonKey)
            .header("Authorization", "Bearer ${SupabaseConfig.anonKey}")
            .header("Accept", "application/json")

    private fun execute(request: Request): JsonArray {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsed = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                val code = parsed?.get("code")?.takeIf { !it.isJsonNull }?.asString
                val message = parsed?.get("message")?.takeIf { !it.isJsonNull }?.asString
                    ?: parsed?.get("hint")?.takeIf { !it.isJsonNull }?.asString
                    ?: "HTTP ${response.code}"
                throw SupabaseException(response.code, code, message)
            }
            if (text.isBlank()) return JsonArray()
            return runCatching { JsonParser.parseString(text).asJsonArray }.getOrElse { JsonArray() }
        }
    }

    suspend fun select(table: String, query: String = ""): JsonArray = withContext(Dispatchers.IO) {
        val suffix = if (query.isBlank()) "" else "?$query"
        execute(requestBuilder("${baseUrl()}/rest/v1/$table$suffix").get().build())
    }

    suspend fun insert(
        table: String,
        body: JsonElement,
        onConflict: String? = null,
        prefer: String = "return=representation",
    ): JsonArray = withContext(Dispatchers.IO) {
        val suffix = if (onConflict.isNullOrBlank()) "" else "?on_conflict=$onConflict"
        val request = requestBuilder("${baseUrl()}/rest/v1/$table$suffix")
            .header("Prefer", prefer)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        execute(request)
    }

    suspend fun upsert(table: String, body: JsonElement, onConflict: String): JsonArray =
        insert(
            table = table,
            body = body,
            onConflict = onConflict,
            prefer = "resolution=merge-duplicates,return=representation",
        )

    suspend fun update(table: String, query: String, body: JsonObject): JsonArray = withContext(Dispatchers.IO) {
        val suffix = if (query.isBlank()) "" else "?$query"
        val request = requestBuilder("${baseUrl()}/rest/v1/$table$suffix")
            .header("Prefer", "return=representation")
            .patch(body.toString().toRequestBody(jsonMediaType))
            .build()
        execute(request)
    }

    suspend fun delete(table: String, query: String) = withContext(Dispatchers.IO) {
        val suffix = if (query.isBlank()) "" else "?$query"
        execute(requestBuilder("${baseUrl()}/rest/v1/$table$suffix").delete().build())
        Unit
    }
}
