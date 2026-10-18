package com.example.ytclient.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.ytclient.data.InnerTube
import com.example.ytclient.data.PlayData
import com.example.ytclient.data.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun newCpn(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    return (1..16).map { chars.random() }.joinToString("")
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(video: Video, onPlay: (Video) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cpn = remember(video.id) { newCpn() }
    val player = remember(video.id) { ExoPlayer.Builder(ctx).build() }
    var related by remember(video.id) { mutableStateOf(listOf<Video>()) }
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    var data by remember(video.id) { mutableStateOf<PlayData?>(null) }
    var liked by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        runCatching { InnerTube.related(video.id) }.onSuccess { related = it.filter { v -> v.id != video.id } }
        runCatching {
            val d = InnerTube.player(video.id, cpn); data = d
            val dsf = DefaultHttpDataSource.Factory().setUserAgent(d.userAgent).setAllowCrossProtocolRedirects(true)
            val pf = ProgressiveMediaSource.Factory(dsf)
            if (d.videoUrl != null && d.audioUrl != null) {
                player.setMediaSource(MergingMediaSource(
                    pf.createMediaSource(MediaItem.fromUri(d.videoUrl)),
                    pf.createMediaSource(MediaItem.fromUri(d.audioUrl))))
            } else if (d.muxedUrl != null) {
                player.setMediaSource(pf.createMediaSource(MediaItem.fromUri(d.muxedUrl)))
            } else error("Stream URL nahi mila")
            player.prepare(); player.playWhenReady = true
            // history record: playback start ping
            InnerTube.ping(d.tracking.playbackUrl, cpn)
        }.onFailure { error = it.message }
    }

    // Watchtime pings (har 30s + exit pe final) = algorithm ko batata hai ki tumne kitna dekha
    suspend fun watchtime(final: Boolean) {
        val d = data ?: return
        val pos = player.currentPosition / 1000.0
        val len = if (d.lengthSec > 0) d.lengthSec.toDouble() else player.duration.coerceAtLeast(0) / 1000.0
        val st = "0.0"; val et = "%.3f".format(pos)
        InnerTube.ping(d.tracking.watchtimeUrl, cpn,
            "&cmt=$et&st=$st&et=$et&len=$len&state=${if (final) "paused" else "playing"}&volume=100" + if (final) "&final=1" else "")
    }
    LaunchedEffect(data) {
        while (data != null) { delay(30_000); if (player.isPlaying) watchtime(false) }
    }
    DisposableEffect(video.id) {
        onDispose {
            val d = data
            if (d != null) kotlinx.coroutines.GlobalScope.launch { watchtime(true) }
            player.release()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Row(Modifier.padding(12.dp)) {
            Text(video.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            IconButton({ liked = true; scope.launch { runCatching { InnerTube.like(video.id) } } }) {
                Icon(Icons.Default.ThumbUp, "Like", tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        error?.let { Text("Error: $it", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        LazyColumn { items(related, key = { it.id }) { VideoRow(it) { onPlay(it) } } }
    }
}
