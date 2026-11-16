package com.olbyine.music.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olbyine.music.data.NeteaseArtist
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteaseCollectionKind
import com.olbyine.music.data.NeteaseMvSummary
import com.olbyine.music.data.NeteasePlaylistSummary
import com.olbyine.music.data.NeteaseSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val NeteaseRed = Color(0xFFE5303C)

private enum class SearchType(val label: String, val code: Int) {    SONG("单曲", 1),
    ARTIST("歌手", 100),
    ALBUM("专辑", 10),
    PLAYLIST("歌单", 1000),
    MV("MV", 1004),
    PODCAST("播客", 1009),
}

/** 搜索历史落在 SharedPreferences，和「清空」「单条删」都不走网络。 */
private object SearchHistory {
    private const val FILE = "olbyine_search"
    private const val KEY = "history"

    fun read(context: android.content.Context): List<String> =
        context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
            .getString(KEY, null).orEmpty().split('\u0001').filter(String::isNotBlank)

    fun write(context: android.content.Context, values: List<String>) {
        context.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).edit()
            .putString(KEY, values.joinToString("\u0001")).apply()
    }
}

/** 把命中的关键词染成主色，其余保持原样。 */
internal fun highlightText(text: String, keyword: String): AnnotatedString {
    if (keyword.isBlank()) return AnnotatedString(text)
    val lower = text.lowercase()
    val needle = keyword.lowercase()
    val at = lower.indexOf(needle)
    if (at < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, at))
        withStyle(SpanStyle(color = NeteaseRed, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(at, at + needle.length))
        }
        append(text.substring(at + needle.length))
    }
}

@Composable
internal fun NeteaseSearch(
    client: NeteaseClient,
    bottomPadding: Dp,
    currentTrackId: String?,
    onOpen: (NeteasePlaylistSummary) -> Unit,
    onPlay: (List<NeteaseSong>, Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(SearchType.SONG) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val history = remember { mutableStateListOf<String>().also { it += SearchHistory.read(context) } }
    val (hot, retryHot) = rememberLoad(Unit) { client.hotSearches() }

    val songs = remember(keyword, type) { mutableStateListOf<NeteaseSong>() }
    val collections = remember(keyword, type) { mutableStateListOf<NeteasePlaylistSummary>() }
    val artists = remember(keyword, type) { mutableStateListOf<NeteaseArtist>() }
    val mvs = remember(keyword, type) { mutableStateListOf<NeteaseMvSummary>() }
    val podcasts = remember(keyword, type) { mutableStateListOf<NeteaseClient.Podcast>() }
    var loading by remember(keyword, type) { mutableStateOf(false) }
    var error by remember(keyword, type) { mutableStateOf<String?>(null) }
    var total by remember(keyword, type) { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()

    val loaded = songs.size + collections.size + artists.size + mvs.size + podcasts.size
    val loadMore: () -> Unit = {
        if (keyword.isNotBlank() && !loading && (total < 0 || loaded < total)) {
            loading = true
            error = null
            scope.launch {
                try {
                    val page = client.search(keyword, type.code, loaded)
                    val batch = page.songs.size + page.playlists.size + page.albums.size +
                        page.artists.size + page.mvs.size + page.podcasts.size
                    total = if (page.total > 0) page.total else loaded + batch
                    songs += page.songs
                    collections += page.playlists + page.albums
                    artists += page.artists
                    mvs += page.mvs
                    podcasts += page.podcasts
                    if (batch == 0) total = loaded
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    error = failure.message ?: "搜索失败"
                }
                loading = false
            }
        }
    }
    LaunchedEffect(keyword, type) { loadMore() }

    val submit: (String) -> Unit = { query ->
        val trimmed = query.trim()
        input = query
        keyword = trimmed
        focusManager.clearFocus()
        if (trimmed.isNotBlank()) {
            history.remove(trimmed)
            history.add(0, trimmed)
            while (history.size > 12) history.removeAt(history.lastIndex)
            SearchHistory.write(context, history)
        }
    }
    val clearAll: () -> Unit = {
        input = ""
        keyword = ""
        type = SearchType.SONG
    }
    val showResults = keyword.isNotBlank()
    // 输入了但还没提交时给联想
    val suggesting = !showResults && input.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OlbyineSearchField(
                value = input,
                onValueChange = {
                    input = it
                    if (it.isBlank()) keyword = ""
                },
                placeholder = "搜索歌曲、歌手、歌单、专辑",
                modifier = Modifier.weight(1f),
                onSearch = { submit(input) },
            )
            AnimatedVisibility(
                visible = input.isNotBlank() || showResults,
                enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = .88f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = .88f),
            ) {
                Text(
                    "取消",
                    Modifier.clip(CircleShape).clickable(onClick = clearAll)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedContent(
            targetState = when {
                showResults -> 2
                suggesting -> 1
                else -> 0
            },
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "search-phase",
        ) { phase ->
            when (phase) {
                2 -> SearchResults(
                    type = type,
                    onType = { type = it },
                    keyword = keyword,
                    songs = songs,
                    collections = collections,
                    artists = artists,
                    mvs = mvs,
                    podcasts = podcasts,
                    loading = loading,
                    error = error,
                    total = total,
                    currentTrackId = currentTrackId,
                    bottomPadding = bottomPadding,
                    onLoadMore = loadMore,
                    onOpen = onOpen,
                    onPlay = onPlay,
                    onRetryHot = retryHot,
                    onSubmit = submit,
                )
                else -> SearchLanding(
                    suggesting = phase == 1,
                    input = input,
                    history = history,
                    hot = hot.value.orEmpty(),
                    hotLoading = hot.loading,
                    hotError = hot.error,
                    bottomPadding = bottomPadding,
                    onSubmit = submit,
                    onDeleteHistory = { word ->
                        history.remove(word)
                        SearchHistory.write(context, history)
                    },
                    onClearHistory = {
                        history.clear()
                        SearchHistory.write(context, emptyList())
                    },
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ 搜前 / 联想 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    suggesting: Boolean,
    input: String,
    history: List<String>,
    hot: List<String>,
    hotLoading: Boolean,
    hotError: String?,
    bottomPadding: Dp,
    onSubmit: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    val query = input.trim()
    val matches = remember(query, hot, history.toList()) {
        if (query.isBlank()) emptyList()
        else (history + hot).map { it.trim() }.filter { it.isNotBlank() && it != query }
            .distinct().filter { it.contains(query, ignoreCase = true) }.take(6)
    }
    val words = remember(hot) { hot.take(20) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (suggesting) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "go") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(NeteaseRed.copy(alpha = .1f))
                        .clickable { onSubmit(query) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StyledIcon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = NeteaseRed)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        buildAnnotatedString {
                            append("搜索 ")
                            withStyle(SpanStyle(color = NeteaseRed, fontWeight = FontWeight.SemiBold)) { append("“$query”") }
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StyledIcon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp), tint = NeteaseRed)
                }
            }
            if (matches.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "matches-title") {
                    Overline("猜你要找", Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp))
                }
                itemsIndexed(matches, key = { index, it -> "m:$index:$it" }) { _, word ->
                    Row(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .6f))
                            .clickable { onSubmit(word) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StyledIcon(Icons.Rounded.NorthWest, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(9.dp))
                        Text(highlightText(word, query), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            return@LazyVerticalGrid
        }

        if (history.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "history-title") {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    StyledIcon(Icons.Rounded.History, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(7.dp))
                    Text("最近搜索", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "清空",
                        Modifier.clip(CircleShape).clickable(onClick = onClearHistory).padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "history") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.forEach { word ->
                        Row(
                            Modifier.clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .82f))
                                .clickable { onSubmit(word) }
                                .padding(start = 14.dp, end = 7.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(word, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Spacer(Modifier.width(5.dp))
                            StyledIcon(
                                Icons.Rounded.Close, "删除", Modifier.size(14.dp).clickable { onDeleteHistory(word) },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "hot-title") {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                StyledIcon(Icons.Rounded.Whatshot, null, Modifier.size(17.dp), tint = NeteaseRed)
                Spacer(Modifier.width(7.dp))
                Text("热搜榜", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("实时更新", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (words.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "hot-state") {
                if (hotLoading) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { SearchSkeletonBar(Modifier.fillMaxWidth().height(46.dp)) }
                    }
                } else {
                    Text(
                        hotError ?: "热搜暂时取不到",
                        Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        itemsIndexed(words, key = { index, it -> "hot:$index:$it" }) { index, word ->
            HotWordCard(index, word, onSubmit, itemMotion())
        }
    }
}

@Composable
private fun HotWordCard(rank: Int, word: String, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val accent = when {
        rank == 0 -> NeteaseRed
        rank == 1 -> Color(0xFFFF6B3D)
        rank == 2 -> Color(0xFFFFA53D)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val tag = when {
        rank < 2 -> "爆"
        rank < 5 -> "热"
        rank < 9 -> "新"
        else -> null
    }
    val tagColor = when {
        rank < 2 -> NeteaseRed
        rank < 5 -> Color(0xFFFF7A29)
        else -> Color(0xFF3E8BFF)
    }
    val heat = ((20 - rank).coerceAtLeast(1)) / 20f
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .62f))
            .clickable { onSubmit(word) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$rank",
                Modifier.width(20.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (rank < 3) FontWeight.Bold else FontWeight.Medium,
                color = accent,
                fontSize = if (rank < 3) 17.sp else 14.sp,
            )
            Spacer(Modifier.width(7.dp))
            Text(word, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (tag != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    tag,
                    Modifier.clip(RoundedCornerShape(4.dp)).background(tagColor.copy(alpha = .16f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = tagColor,
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(2.5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))) {
            Box(
                Modifier.fillMaxWidth(heat).height(2.5.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .5f), accent))),
            )
        }
    }
}

/* ------------------------------------------------------------------ 结果 */

@Composable
private fun SearchResults(
    type: SearchType,
    onType: (SearchType) -> Unit,
    keyword: String,
    songs: List<NeteaseSong>,
    collections: List<NeteasePlaylistSummary>,
    artists: List<NeteaseArtist>,
    mvs: List<NeteaseMvSummary>,
    podcasts: List<NeteaseClient.Podcast>,
    loading: Boolean,
    error: String?,
    total: Int,
    currentTrackId: String?,
    bottomPadding: Dp,
    onLoadMore: () -> Unit,
    onOpen: (NeteasePlaylistSummary) -> Unit,
    onPlay: (List<NeteaseSong>, Int) -> Unit,
    onRetryHot: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PillTabs(
            items = SearchType.entries.map { it to it.label },
            selected = type,
            onSelect = onType,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (type) {
                SearchType.SONG -> songs.forEachIndexed { index, song ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "s$index:${song.id}") {
                        if (index >= songs.size - 6) LaunchedEffect(songs.size) { onLoadMore() }
                        MotionItem {
                            NeteaseSongRow(
                                song = song,
                                current = currentTrackId == NeteaseSong.trackId(song.id),
                                onClick = { onPlay(songs.toList(), index) },
                                mark = keyword,
                            )
                        }
                    }
                }

                SearchType.ARTIST -> itemsIndexed(artists, key = { index, it -> "a$index:${it.id}" }) { index, artist ->
                    if (index >= artists.size - 6) LaunchedEffect(artists.size) { onLoadMore() }
                    Column(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .clickable {
                                onOpen(
                                    NeteasePlaylistSummary(
                                        artist.id, artist.name + " · 热门歌曲", artist.pictureUrl, 50, artist.name,
                                        false, false, kind = NeteaseCollectionKind.ARTIST,
                                    ),
                                )
                            }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(84.dp)) {
                            UrlImage(
                                artist.pictureUrl?.let { "$it?param=200y200" },
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), CircleShape),
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(
                            highlightText(artist.name, keyword),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "${artist.albumCount} 张专辑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SearchType.MV -> mvs.forEachIndexed { index, mv ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "mv$index:${mv.id}") {
                        if (index >= mvs.size - 6) LaunchedEffect(mvs.size) { onLoadMore() }
                        val push = LocalNcmPush.current
                        Row(
                            itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable { push?.invoke(NcmRoute.Mv(mv.id)) }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(104.dp).height(59.dp)) {
                                UrlImage(
                                    mv.coverUrl?.let { "$it?param=320y180" },
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                                Box(
                                    Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp))
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .34f)))),
                                )
                                Box(
                                    Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape)
                                        .background(Color.Black.copy(alpha = .42f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    StyledIcon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp), tint = Color.White)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    highlightText(mv.name, keyword),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        mv.artist.takeIf(String::isNotBlank),
                                        mv.playCount.takeIf { it > 0 }?.let { "播放 " + playCountLabel(it) },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                SearchType.PODCAST -> itemsIndexed(podcasts, key = { index, it -> "pd$index:${it.id}" }) { index, podcast ->
                    if (index >= podcasts.size - 6) LaunchedEffect(podcasts.size) { onLoadMore() }
                    val push = LocalNcmPush.current
                    Column(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .clickable { push?.invoke(NcmRoute.Podcast(podcast)) }
                            .padding(6.dp),
                    ) {
                        UrlImage(
                            podcast.coverUrl?.let { "$it?param=400y400" },
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            highlightText(podcast.name, keyword),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                podcast.host.takeIf(String::isNotBlank),
                                podcast.programCount.takeIf { it > 0 }?.let { "$it 期" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                else -> itemsIndexed(collections, key = { index, it -> "c$index:${it.id}" }) { index, collection ->
                    if (index >= collections.size - 6) LaunchedEffect(collections.size) { onLoadMore() }
                    Column(
                        itemMotion().fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .clickable { onOpen(collection) }
                            .padding(6.dp),
                    ) {
                        UrlImage(
                            collection.coverUrl?.let { "$it?param=400y400" },
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .clip(RoundedCornerShape(if (type == SearchType.ALBUM) 10.dp else 14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            highlightText(collection.name, keyword),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = if (type == SearchType.ALBUM) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                if (type == SearchType.ALBUM) "${collection.trackCount} 首"
                                else "${collection.trackCount} 首",
                                collection.creator.takeIf { it.isNotBlank() },
                                collection.playCount.takeIf { it > 0 }?.let { playCountLabel(it) + " 播放" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            val current = when (type) {
                SearchType.SONG -> songs.size
                SearchType.ARTIST -> artists.size
                SearchType.ALBUM, SearchType.PLAYLIST -> collections.size
                SearchType.MV -> mvs.size
                SearchType.PODCAST -> podcasts.size
            }
            if (current == 0 && loading) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "skeleton") {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { SearchSkeletonBar(Modifier.fillMaxWidth().height(58.dp)) }
                    }
                }
            } else if (loading || error != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "state") {
                    LoadingOrError(loading, error, onLoadMore, itemMotion())
                }
            } else if (current == 0 && total == 0) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    SearchEmpty(keyword, onSubmit)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchEmpty(keyword: String, onSubmit: (String) -> Unit) {
    val hints = listOf("换一个更短的关键词", "试试歌手名或专辑名", "检查一下有没有打错字")
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .7f)),
            contentAlignment = Alignment.Center,
        ) {
            StyledIcon(Icons.Rounded.SearchOff, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "没有找到「$keyword」",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            hints.first(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("周杰伦", "告五人", "华语流行", "电子").forEach { word ->
                Text(
                    word,
                    Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .85f))
                        .clickable { onSubmit(word) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun SearchSkeletonBar(modifier: Modifier) {
    Box(modifier.skeleton(RoundedCornerShape(14.dp)))
}
