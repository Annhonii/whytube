package com.example.ytclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.ytclient.data.Session
import com.example.ytclient.data.Video
import com.example.ytclient.ui.HomeScreen
import com.example.ytclient.ui.LoginScreen
import com.example.ytclient.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    var loggedIn by remember { mutableStateOf(Session.loggedIn) }
                    var playing by remember { mutableStateOf<Video?>(null) }
                    when {
                        !loggedIn -> LoginScreen { loggedIn = true }
                        playing != null -> {
                            BackHandler { playing = null }
                            PlayerScreen(playing!!) { playing = it }
                        }
                        else -> HomeScreen(onPlay = { playing = it }, onLogout = { Session.logout(); loggedIn = false })
                    }
                }
            }
        }
    }
}
