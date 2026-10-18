package com.example.ytclient.data

import java.security.MessageDigest

object Auth {
    const val ORIGIN = "https://www.youtube.com"

    private fun cookieValue(cookie: String, name: String) =
        cookie.split(";").map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")

    /** Authorization: SAPISIDHASH <ts>_<sha1(ts SAPISID origin)> */
    fun sapisidHash(cookie: String): String? {
        val sapisid = cookieValue(cookie, "SAPISID") ?: cookieValue(cookie, "__Secure-3PAPISID") ?: return null
        val ts = System.currentTimeMillis() / 1000
        val sha = MessageDigest.getInstance("SHA-1").digest("$ts $sapisid $ORIGIN".toByteArray())
        return "SAPISIDHASH ${ts}_${sha.joinToString("") { "%02x".format(it) }}"
    }
}
