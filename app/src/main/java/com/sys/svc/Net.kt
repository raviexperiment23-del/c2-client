package com.sys.svc
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
object Net {
    const val BASE = "http://10.0.2.2:8443"
    val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()
    fun postJson(path: String, body: JSONObject): String? {
        return try {
            val req = Request.Builder().url(BASE + path).post(body.toString().toRequestBody(JSON)).build()
            http.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) { null }
    }
    fun get(path: String): String? {
        return try {
            val req = Request.Builder().url(BASE + path).get().build()
            http.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) { null }
    }
}
