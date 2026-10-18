package com.example.ytclient.data

import org.json.JSONArray
import org.json.JSONObject

data class Video(val id: String, val title: String, val channel: String, val thumb: String, val duration: String = "")

data class Tracking(val playbackUrl: String?, val watchtimeUrl: String?)

data class PlayData(
    val videoUrl: String?, val audioUrl: String?, val muxedUrl: String?,
    val userAgent: String, val tracking: Tracking, val lengthSec: Long, val title: String,
)

/** JSON me recursively har object visit karo. */
fun walk(node: Any?, f: (JSONObject) -> Unit) {
    when (node) {
        is JSONObject -> { f(node); node.keys().forEach { walk(node.opt(it), f) } }
        is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), f)
    }
}

/** Safe path access: at("a", 0, "b") */
fun Any?.at(vararg path: Any): Any? {
    var cur: Any? = this
    for (p in path) {
        cur = when {
            p is String && cur is JSONObject -> cur.opt(p)
            p is Int && cur is JSONArray -> cur.opt(p)
            else -> return null
        }
    }
    return cur
}
fun Any?.str(vararg path: Any): String = (at(*path) as? String).orEmpty()

private fun text(o: Any?): String =
    o.str("simpleText").ifEmpty { o.str("runs", 0, "text") }.ifEmpty { o.str("content") }

object Parser {
    fun videos(root: JSONObject): List<Video> {
        val out = LinkedHashMap<String, Video>()
        walk(root) { o ->
            for (key in listOf("videoRenderer", "compactVideoRenderer", "gridVideoRenderer")) {
                val r = o.optJSONObject(key) ?: continue
                val id = r.optString("videoId"); if (id.isEmpty()) continue
                val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url") ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                out.putIfAbsent(id, Video(
                    id, text(r.opt("title")),
                    text(r.opt("ownerText")).ifEmpty { text(r.opt("longBylineText")) }.ifEmpty { text(r.opt("shortBylineText")) },
                    thumb, text(r.opt("lengthText"))))
            }
            val l = o.optJSONObject("lockupViewModel")
            if (l != null && l.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO") {
                val id = l.optString("contentId")
                if (id.isNotEmpty()) {
                    val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    out.putIfAbsent(id, Video(
                        id, meta.str("title", "content"),
                        meta.str("metadata", "contentMetadataViewModel", "metadataRows", 0, "metadataParts", 0, "text", "content"),
                        "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
                }
            }
        }
        return out.values.toList()
    }

    fun continuation(root: JSONObject): String? {
        var tok: String? = null
        walk(root) { o -> if (tok == null) o.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }?.let { tok = it } }
        return tok
    }
}
