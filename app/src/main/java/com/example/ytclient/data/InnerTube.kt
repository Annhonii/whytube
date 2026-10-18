package com.example.ytclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Unofficial InnerTube client.
 *  - WEB (logged-in, cookies)  -> home feed, related, search, playbackTracking (history/algorithm)
 *  - ANDROID_VR (no auth)      -> direct stream URLs jinko PoToken/signature nahi chahiye
 */
object InnerTube {
    // Agar WEB calls fail ho, ye version bump karo (youtube.com ke source se 'INNERTUBE_CONTEXT_CLIENT_VERSION' dekho)
    private const val WEB_VERSION = "2.20260101.00.00"
    private const val WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    const val VR_UA = "com.google.android.apps.youtube.vr.oculus/1.57.29 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val JSON = "application/json".toMediaType()

    private fun webContext() = JSONObject().put("client", JSONObject()
        .put("clientName", "WEB").put("clientVersion", WEB_VERSION).put("hl", "en").put("gl", "US"))

    private fun vrContext() = JSONObject().put("client", JSONObject()
        .put("clientName", "ANDROID_VR").put("clientVersion", "1.57.29")
        .put("deviceMake", "Oculus").put("deviceModel", "Quest 3")
        .put("androidSdkVersion", 32).put("osName", "Android").put("osVersion", "12L")
        .put("userAgent", VR_UA).put("hl", "en").put("gl", "US"))

    fun authHeaders(b: Request.Builder): Request.Builder {
        val cookie = Session.cookie ?: return b
        b.header("Cookie", cookie).header("X-Goog-AuthUser", "0")
            .header("X-Origin", Auth.ORIGIN).header("Origin", Auth.ORIGIN).header("Referer", Auth.ORIGIN + "/")
        Auth.sapisidHash(cookie)?.let { b.header("Authorization", it) }
        return b
    }

    private suspend fun post(endpoint: String, body: JSONObject, vr: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        body.put("context", if (vr) vrContext() else webContext())
        val b = Request.Builder().url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON))
        if (vr) {
            b.header("User-Agent", VR_UA).header("X-Youtube-Client-Name", "28").header("X-Youtube-Client-Version", "1.57.29")
        } else {
            b.header("User-Agent", WEB_UA).header("X-Youtube-Client-Name", "1").header("X-Youtube-Client-Version", WEB_VERSION)
            authHeaders(b)
        }
        http.newCall(b.build()).execute().use { JSONObject(it.body?.string().orEmpty()) }
    }

    /** Personalized home feed = tumhara recommendation algorithm. */
    suspend fun home(continuation: String? = null): Pair<List<Video>, String?> {
        val body = if (continuation == null) JSONObject().put("browseId", "FEwhat_to_watch")
                   else JSONObject().put("continuation", continuation)
        val r = post("browse", body)
        return Parser.videos(r) to Parser.continuation(r)
    }

    suspend fun related(videoId: String): List<Video> =
        Parser.videos(post("next", JSONObject().put("videoId", videoId)))

    suspend fun search(q: String): List<Video> =
        Parser.videos(post("search", JSONObject().put("query", q)))

    suspend fun like(videoId: String) {
        post("like/like", JSONObject().put("target", JSONObject().put("videoId", videoId)))
    }

    /** Streams VR client se, tracking URLs WEB (logged-in) client se. */
    suspend fun player(videoId: String, cpn: String): PlayData {
        val vr = post("player", JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true), vr = true)
        val web = post("player", JSONObject().put("videoId", videoId).put("cpn", cpn).put("contentCheckOk", true).put("racyCheckOk", true))

        val sd = vr.optJSONObject("streamingData")
        val adaptive = sd?.optJSONArray("adaptiveFormats")
        var bestV: JSONObject? = null; var bestA: JSONObject? = null
        if (adaptive != null) for (i in 0 until adaptive.length()) {
            val f = adaptive.getJSONObject(i)
            if (!f.has("url")) continue
            val mime = f.optString("mimeType")
            if (mime.startsWith("video/mp4") && mime.contains("avc1") && f.optInt("height") <= 1080) {
                if (bestV == null || f.optInt("height") > bestV.optInt("height") ||
                    (f.optInt("height") == bestV.optInt("height") && f.optInt("bitrate") > bestV.optInt("bitrate"))) bestV = f
            } else if (mime.startsWith("audio/mp4")) {
                if (bestA == null || f.optInt("bitrate") > bestA.optInt("bitrate")) bestA = f
            }
        }
        var muxed: String? = null
        sd?.optJSONArray("formats")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).optString("url").takeIf { it.isNotEmpty() }?.let { muxed = it } }

        val pt = web.optJSONObject("playbackTracking")
        val tr = Tracking(pt.str("videostatsPlaybackUrl", "baseUrl").ifEmpty { null }, pt.str("videostatsWatchtimeUrl", "baseUrl").ifEmpty { null })
        val vd = vr.optJSONObject("videoDetails")
        return PlayData(bestV?.optString("url"), bestA?.optString("url"), muxed, VR_UA, tr,
            vd?.optString("lengthSeconds")?.toLongOrNull() ?: 0, vd?.optString("title").orEmpty())
    }

    /** Watch history + algorithm update ke liye tracking pings. */
    suspend fun ping(url: String?, cpn: String, extra: String = "") {
        if (url == null) return
        withContext(Dispatchers.IO) {
            runCatching {
                val u = "$url&ver=2&c=WEB&cver=$WEB_VERSION&cpn=$cpn$extra"
                val b = Request.Builder().url(u).header("User-Agent", WEB_UA)
                authHeaders(b)
                http.newCall(b.build()).execute().close()
            }
        }
    }
}
