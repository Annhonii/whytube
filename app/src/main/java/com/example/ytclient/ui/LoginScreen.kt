package com.example.ytclient.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import com.example.ytclient.data.Session

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
        WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Google embedded WebView block karta hai ("; wv" hatao)
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (url.startsWith("https://www.youtube.com") || url.startsWith("https://m.youtube.com")) {
                        val c = CookieManager.getInstance().getCookie("https://www.youtube.com")
                        if (c != null && c.contains("SAPISID=")) { Session.cookie = c; onLoggedIn() }
                    }
                }
            }
            loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
        }
    })
}
