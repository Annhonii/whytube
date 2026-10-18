package com.example.ytclient.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ytclient.data.InnerTube
import com.example.ytclient.data.Video
import kotlinx.coroutines.launch

@Composable
fun VideoRow(v: Video, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        AsyncImage(v.thumb, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
        Text(v.title, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), maxLines = 2, style = MaterialTheme.typography.titleSmall)
        Text(listOf(v.channel, v.duration).filter { it.isNotEmpty() }.joinToString(" • "),
            Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onPlay: (Video) -> Unit, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf(listOf<Video>()) }
    var cont by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val state = rememberLazyListState()

    fun loadHome(more: Boolean) = scope.launch {
        if (loading) return@launch
        loading = true; error = null
        runCatching { InnerTube.home(if (more) cont else null) }
            .onSuccess { (v, c) -> videos = if (more) (videos + v).distinctBy { it.id } else v; cont = c }
            .onFailure { error = it.message }
        loading = false
    }
    fun doSearch() = scope.launch {
        if (query.isBlank()) { searching = false; loadHome(false); return@launch }
        loading = true; searching = true; error = null
        runCatching { InnerTube.search(query) }.onSuccess { videos = it; cont = null }.onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(Unit) { loadHome(false) }
    val nearEnd by remember { derivedStateOf { val l = state.layoutInfo; (l.visibleItemsInfo.lastOrNull()?.index ?: 0) >= l.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd) { if (nearEnd && cont != null && !searching) loadHome(true) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { doSearch() }))
            IconButton(onLogout) { Icon(Icons.Default.Logout, "Logout") }
        }
        error?.let { Text("Error: $it", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error) }
        if (loading && videos.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(state = state) { items(videos, key = { it.id }) { VideoRow(it) { onPlay(it) } } }
    }
}
