package com.example.ytclient.data

import android.content.Context

/** Login ke baad WebView se mili cookies yaha store hoti hain. */
object Session {
    private var prefs: android.content.SharedPreferences? = null
    fun init(ctx: Context) { prefs = ctx.getSharedPreferences("session", Context.MODE_PRIVATE) }
    var cookie: String?
        get() = prefs?.getString("cookie", null)
        set(v) { prefs?.edit()?.putString("cookie", v)?.apply() }
    val loggedIn get() = cookie?.contains("SAPISID=") == true
    fun logout() { cookie = null }
}
