@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.olbyine.music.ui

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.olbyine.music.data.HapticArea
import com.olbyine.music.data.NeteaseArtist
import com.olbyine.music.data.NeteaseClient
import com.olbyine.music.data.NeteaseCollectionKind
import com.olbyine.music.data.NeteaseComment
import com.olbyine.music.data.NeteaseMessage
import com.olbyine.music.data.NeteasePlaylistSummary
import com.olbyine.music.data.NeteaseSong
import com.olbyine.music.data.NeteaseThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val LocalNeteaseComments = staticCompositionLocalOf<((Long, String) -> Unit)?> { null }

internal enum class NeteaseTab(val label: String) {
    MINE("我的"), DISCOVER("发现"), CHARTS("排行榜"), SEARCH("搜索"), MESSAGES("消息"),
}

private val NeteaseRed = Color(0xFFE5303C)

internal fun playCountLabel(count: Long): String = when {
    count >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", count / 100_000_000.0)
    count >= 10_000 -> String.format(Locale.CHINA, "%.1f万", count / 10_000.0)
    else -> count.toString()
}

internal class Loaded<T>(val value: T? = null, val loading: Boolean = false, val error: String? = null)

@Composable
internal fun <T> rememberLoad(vararg keys: Any?, load: suspend () -> T): Pair<Loaded<T>, () -> Unit> {
    var state by remember(*keys) { mutableStateOf(Loaded<T>(loading = true)) }
    var attempt by remember(*keys) { mutableStateOf(0) }
    LaunchedEffect(*keys, attempt) {
        state = Loaded(state.value, loading = true)
        state = try {
            Loaded(load())
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Loaded(state.value, error = error.message ?: "加载失败，请检查网络")
        }
    }
    return state to { attempt++ }
}

@Composable
internal fun <T> rememberLoadStream(vararg keys: Any?, load: () -> kotlinx.coroutines.flow.Flow<T>): Pair<Loaded<T>, () -> Unit> {
    var state by remember(*keys) { mutableStateOf(Loaded<T>(loading = true)) }
    var attempt by remember(*keys) { mutableStateOf(0) }
    LaunchedEffect(*keys, attempt) {
        state = Loaded(state.value, loading = true)
        try {
            load().collect { value -> state = Loaded(value) }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            state = Loaded(state.value, error = error.message ?: "加载失败，请检查网络")
        }
    }
    return state to { attempt++ }
}

@Composable
internal fun LoadingOrError(loading: Boolean, error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier, accent: Color = Color.Unspecified) {
    val reduce = LocalReduceMotion.current
    Box(modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedContent(
            targetState = when {
                loading -> LoadPhase.Loading
                error != null -> LoadPhase.Error(error)
                else -> LoadPhase.Idle
            },
            contentKey = { it::class },
            transitionSpec = {
                val enter = androidx.compose.animation.fadeIn(tween(220, easing = OlbyineTokens.Motion.easeOut)) +
                    if (reduce) androidx.compose.animation.EnterTransition.None
                    else androidx.compose.animation.slideInVertically(tween(260, easing = OlbyineTokens.Motion.easeOut)) { it / 6 }
                enter togetherWith androidx.compose.animation.fadeOut(tween(100))
            },
            contentAlignment = Alignment.Center,
            label = "load-phase",
        ) { phase ->
            when (phase) {
                LoadPhase.Loading -> DelayedSpinner(accent.takeOrElse { MaterialTheme.colorScheme.primary })
                is LoadPhase.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phase.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    PillButton("重试", onRetry, style = PillStyle.TONAL)
                }
                LoadPhase.Idle -> Spacer(Modifier.size(1.dp))
            }
        }
    }
}

@Composable
private fun DelayedSpinner(color: Color) {
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        shown.animateTo(1f, tween(220, easing = OlbyineTokens.Motion.easeOut))
    }
    OlbyineSpinner(
        Modifier.size(28.dp).graphicsLayer { alpha = shown.value },
        strokeWidth = 2.5.dp,
        color = color,
    )
}

private sealed interface LoadPhase {
    data object Loading : LoadPhase
    data class Error(val message: String) : LoadPhase
    data object Idle : LoadPhase
}

@Composable
internal fun NeteaseCoverCard(playlist: NeteasePlaylistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            UrlImage(
                playlist.coverUrl?.let { "$it?param=400y400" },
                Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            if (playlist.playCount > 0) {
                Text(
                    "▶ " + playCountLabel(playlist.playCount),
                    Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = .38f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    color = Color.White,
                )
            }
        }
        Text(playlist.name, Modifier.padding(start = 2.dp, top = 7.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val sub = listOf(playlist.note, playlist.creator).firstOrNull { it.isNotBlank() }
        if (sub != null) {
            Text(sub, Modifier.padding(start = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun NeteaseDiscover(
    client: NeteaseClient,
    signedIn: Boolean,
    bottomPadding: Dp,
    onOpen: (NeteasePlaylistSummary) -> Unit,
    onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit = { _, _, _ -> },
) {
    val (recommended, retryRecommended) = rememberLoad(signedIn) { if (signedIn) client.recommendedPlaylists() else emptyList() }
    val (daily, retryDaily) = rememberLoad(signedIn) { if (signedIn) client.dailySongs() else emptyList() }
    val (categories, _) = rememberLoad(Unit) { client.playlistCategories() }
    val (charts, _) = rememberLoad(Unit) { client.charts() }
    val (podcasts, _) = rememberLoad(Unit) { client.hotPodcasts() }
    var category by rememberSaveable { mutableStateOf("全部") }
    var showAllCategories by remember { mutableStateOf(false) }
    val square = remember(category) { mutableStateListOf<NeteasePlaylistSummary>() }
    var squareLoading by remember(category) { mutableStateOf(false) }
    var squareError by remember(category) { mutableStateOf<String?>(null) }
    var squareEnd by remember(category) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val push = LocalNcmPush.current
    val loadMore: () -> Unit = {
        if (!squareLoading && !squareEnd) {
            squareLoading = true
            squareError = null
            scope.launch {
                try {
                    val page = client.categoryPlaylists(category, square.size)
                    if (page.isEmpty()) squareEnd = true
                    square += page.filter { new -> square.none { it.id == new.id } }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    squareError = error.message ?: "加载失败"
                }
                squareLoading = false
            }
        }
    }
    LaunchedEffect(category) { loadMore() }
    val hotCategories = remember(categories.value) {
        listOf("全部") + categories.value.orEmpty().flatMap { it.second }.take(14)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "hero") {
            DailyDeckCard(
                client = client,
                daily = daily,
                retryDaily = retryDaily,
                signedIn = signedIn,
                onOpen = onOpen,
                onPlay = onPlay,
            )
        }
        if (signedIn) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "fm") { PersonalFmCard(client, onPlay) }
        }
        val picks = recommended.value.orEmpty()
        if (picks.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "rec-title") { SectionTitle("为你推荐") }
            item(span = { GridItemSpan(maxLineSpan) }, key = "rec") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(picks, key = { index, it -> "$index:${it.id}" }) { _, it -> NeteaseCoverCard(it, { onOpen(it) }, itemMotion().width(138.dp)) }
                }
            }
        } else if (recommended.error != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "rec-error") { LoadingOrError(false, recommended.error, retryRecommended) }
        }

        val hot = podcasts.value.orEmpty()
        if (hot.isNotEmpty() && push != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "pod-title") {
                SectionTitle("播客", "更多") { push(NcmRoute.Podcasts) }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "pod") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(hot.take(12), key = { "pod:${it.id}" }) { podcast ->
                        PodcastMiniCard(podcast, { push(NcmRoute.Podcast(podcast)) }, itemMotion().width(140.dp))
                    }
                }
            }
        }

        val tops = charts.value.orEmpty().take(3)
        if (tops.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "chart-title") { SectionTitle("排行榜") }
            item(span = { GridItemSpan(maxLineSpan) }, key = "chart-row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(tops, key = { "top:${it.id}" }) { chart -> NeteaseCoverCard(chart, { onOpen(chart) }, itemMotion().width(138.dp)) }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "square-title") {
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Overline("歌单广场", Modifier.weight(1f))
                if (categories.value != null) {
                    Text(
                        "全部分类",
                        Modifier.clip(CircleShape).clickable { showAllCategories = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "square-tabs") {
            PillTabs(
                items = (if (category in hotCategories) hotCategories else hotCategories + category).map { it to it },
                selected = category,
                onSelect = { category = it },
                contentPadding = PaddingValues(horizontal = 4.dp),
            )
        }
        itemsIndexed(square, key = { index, it -> "sq:$index:${it.id}" }) { index, playlist ->
            if (index >= square.size - 6) LaunchedEffect(square.size) { loadMore() }
            NeteaseCoverCard(playlist, { onOpen(playlist) }, itemMotion())
        }
        if (square.isEmpty() && squareLoading) {
            skeletonCards(6)
        } else if (squareLoading || squareError != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "square-state") { LoadingOrError(squareLoading, squareError, loadMore) }
        }
    }
    if (showAllCategories) {
        GlassBottomSheet(onDismissRequest = { showAllCategories = false }) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 20.dp)) {
                item { Text("歌单分类", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                categories.value.orEmpty().forEach { (group, names) ->
                    item(key = group) {
                        Overline(group, Modifier.padding(top = 16.dp, bottom = 8.dp))
                        FlowChips(names, category) { category = it; showAllCategories = false }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, trailing: String? = null, onTrailing: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Overline(text, Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                Modifier.clip(CircleShape).clickable(onClick = onTrailing).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 叠卡大卡：上滑把当前这张推走、下一张顶上来。跟手位移，松手走弹簧。 */
private enum class DeckTab(
    val title: String,
    val hint: String,
    val start: Color,
    val mid: Color,
    val end: Color,
) {
    DAILY("每日推荐", "每天 6:00 更新", Color(0xFF2B2519), Color(0xFF6B5530), Color(0xFF8C6B37)),
    STYLE("风格推荐", "按你的口味挑一张歌单", Color(0xFF241630), Color(0xFF4E2C68), Color(0xFF7B4A9C)),
    RADAR("私人雷达", "根据口味实时生成", Color(0xFF0F2130), Color(0xFF20475F), Color(0xFF2E7A9B)),
    FM("私人 FM", "无限续播，随听随换", Color(0xFF2A1414), Color(0xFF5E2626), Color(0xFF96392F)),
}

private val deckTabs = DeckTab.entries

/** 网易云「私人雷达」歌单 id，取不到时回退到推荐歌单。 */
private const val RADAR_PLAYLIST_ID = 3136952023L

/** 卡片高度。手势用它换算拖动进度，改这里两处会一起跟着变。 */
private val DeckCardHeight = 186.dp

private class DeckPayload(
    val songs: List<NeteaseSong>,
    val summary: NeteasePlaylistSummary?,
    val note: String,
)

/** 一张卡此刻要显示的东西，四张卡各算各的。 */
private class DeckContent(
    val songs: List<NeteaseSong>,
    val subtitle: String,
    val dateLine: String,
    val summary: NeteasePlaylistSummary?,
)

private suspend fun loadDeckPayload(client: NeteaseClient, tab: DeckTab): DeckPayload = when (tab) {
    DeckTab.DAILY -> DeckPayload(emptyList(), null, "每日推荐")
    DeckTab.FM -> DeckPayload(client.personalFm(), null, "私人 FM")
    DeckTab.RADAR -> {
        val radar = try {
            client.playlistSongs(RADAR_PLAYLIST_ID)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            emptyList()
        }
        if (radar.isNotEmpty()) {
            DeckPayload(radar, null, "私人雷达")
        } else {
            val pick = client.recommendedPlaylists().firstOrNull()
            if (pick == null) DeckPayload(emptyList(), null, "私人雷达")
            else DeckPayload(client.playlistSongs(pick.id), pick, pick.note.ifBlank { "私人雷达" })
        }
    }
    DeckTab.STYLE -> {
        val pick = client.recommendedPlaylists().firstOrNull()
        if (pick == null) DeckPayload(emptyList(), null, "风格推荐")
        else DeckPayload(client.playlistSongs(pick.id), pick, pick.note.ifBlank { "风格推荐" })
    }
}

/** 卡片里的玻璃小牌只在用户开了液态玻璃、且非原生风格、系统支持 RenderEffect 时才做真玻璃。 */
@Composable
private fun deckLiquidGlass(): Boolean {
    if (LocalStyleSpec.current.style == com.olbyine.music.data.UiStyle.MATERIAL) return false
    if (!LocalLiquidGlass.current) return false
    return Build.VERSION.SDK_INT >= 31
}

@Composable
private fun DailyDeckCard(
    client: NeteaseClient,
    daily: Loaded<List<NeteaseSong>>,
    retryDaily: () -> Unit,
    signedIn: Boolean,
    onOpen: (NeteasePlaylistSummary) -> Unit,
    onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(DeckTab.DAILY) }
    var showSheet by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()

    /** 当前这张卡相对原位的位移（px），0 = 就位，-卡高 = 完全推走。 */
    val slide = remember { mutableFloatStateOf(0f) }
    val tabState = rememberUpdatedState(tab)
    val nextTabState = rememberUpdatedState(
        deckTabs[(deckTabs.indexOf(tab).coerceAtLeast(0) + 1) % deckTabs.size],
    )
    val reduceState = rememberUpdatedState(reduceMotion)

    val index = deckTabs.indexOf(tab).coerceAtLeast(0)
    val nextTab = deckTabs[(index + 1) % deckTabs.size]

    // 已拉过的卡留在缓存里，切回来不重复请求；进页面时顺手把下一张预取好，滑动时不至于空着
    val cache = remember { mutableStateMapOf<DeckTab, Loaded<DeckPayload>>() }
    LaunchedEffect(tab, signedIn, attempt) {
        if (!signedIn) return@LaunchedEffect
        for (target in listOf(tab, nextTab)) {
            if (target == DeckTab.DAILY) continue
            val cached = cache[target]
            if (cached?.loading == true || cached?.value != null) continue
            cache[target] = Loaded(error = cached?.error, loading = true)
            cache[target] = try {
                Loaded(loadDeckPayload(client, target))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Loaded(error = error.message ?: "加载失败，请检查网络")
            }
        }
    }

    val dailyDateLine = remember {
        SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(Date()) + " · 每天 6:00 更新"
    }

    fun contentOf(target: DeckTab): DeckContent {
        val dailySongs = daily.value.orEmpty()
        if (target == DeckTab.DAILY) {
            return DeckContent(
                songs = dailySongs,
                subtitle = when {
                    !signedIn -> "登录后每天 6:00 为你更新"
                    daily.error != null -> "加载失败 · 点一下重试"
                    dailySongs.isEmpty() && daily.loading -> "正在挑选今天的歌…"
                    else -> "${artistLine(dailySongs)} 等 ${dailySongs.size.coerceAtLeast(30)} 首"
                },
                dateLine = dailyDateLine,
                summary = NeteasePlaylistSummary(
                    -1L,
                    "每日推荐",
                    dailySongs.firstOrNull()?.coverUrl,
                    dailySongs.size.takeIf { it > 0 } ?: 30,
                    "根据你的口味生成",
                    false,
                    false,
                    kind = NeteaseCollectionKind.DAILY,
                ),
            )
        }
        val payload = cache[target]
        val songs = payload?.value?.songs.orEmpty()
        return DeckContent(
            songs = songs,
            subtitle = when {
                !signedIn -> "登录后为你生成"
                payload?.error != null -> "加载失败 · 点一下重试"
                songs.isEmpty() && payload?.loading != false -> "正在挑选…"
                songs.isEmpty() -> "暂时取不到，点一下重试"
                else -> "${songs.size} 首 · ${payload?.value?.note ?: target.hint}"
            },
            dateLine = target.title + " · " + target.hint,
            summary = payload?.value?.summary,
        )
    }

    fun retry(target: DeckTab) {
        if (target == DeckTab.DAILY) {
            retryDaily()
            return
        }
        cache.remove(target)
        attempt++
    }

    fun open(target: DeckTab) {
        val content = contentOf(target)
        haptics.tick(HapticArea.MENUS)
        when {
            target == DeckTab.DAILY && daily.error != null -> retry(target)
            target != DeckTab.DAILY && cache[target]?.error != null -> retry(target)
            content.summary != null -> onOpen(content.summary)
            content.songs.isNotEmpty() -> onPlay(content.songs, 0, false)
            else -> retry(target)
        }
    }

    /** 一张卡推走之后落到哪张：环形，第四张之后回到日推。 */
    fun commit() {
        tab = nextTabState.value
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(DeckCardHeight)
                .clipToBounds()
                .pointerInput(Unit) {
                    val height = size.height.toFloat()
                    if (height <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (settling) return@awaitEachGesture
                        // 只认向上的拖动：向下留给外面的列表滚动
                        var upward = false
                        val grabbed = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                            if (over < 0f) {
                                change.consume()
                                upward = true
                            }
                        }
                        if (!upward || grabbed == null) return@awaitEachGesture

                        val tracker = VelocityTracker()
                        tracker.resetTracking()
                        tracker.addPosition(grabbed.uptimeMillis, grabbed.position)
                        val start = (grabbed.position.y - down.position.y).coerceIn(-height, 0f)
                        slide.floatValue = start

                        var velocity = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == grabbed.id } ?: break
                            if (!change.pressed) {
                                velocity = tracker.calculateVelocity().y
                                break
                            }
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            slide.floatValue = (slide.floatValue + change.positionChange().y).coerceIn(-height, 0f)
                        }

                        // 松手：推过三成、或者甩得够快，就翻过去；否则弹回来
                        val progress = -slide.floatValue / height
                        val flung = -velocity > 900f
                        if (progress > .28f || flung) {
                            haptics.tick(HapticArea.MENUS)
                            settling = true
                            scope.launch {
                                if (!reduceState.value) {
                                    // 先把这张推干净，再换卡归位，视觉上是同一张卡接着滑出去
                                    animate(slide.floatValue, -height, animationSpec = spring(dampingRatio = .9f, stiffness = 420f)) { value, _ ->
                                        slide.floatValue = value
                                    }
                                }
                                commit()
                                slide.floatValue = 0f
                                settling = false
                            }
                        } else {
                            settling = true
                            scope.launch {
                                animate(slide.floatValue, 0f, animationSpec = spring(dampingRatio = .74f, stiffness = 380f)) { value, _ ->
                                    slide.floatValue = value
                                }
                                settling = false
                            }
                        }
                    }
                },
        ) {
            // 底下那张：静止，等着被顶上来
            DeckPage(
                tab = nextTab,
                content = contentOf(nextTab),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = (-slide.floatValue / size.height).coerceIn(0f, 1f)
                        val scale = .94f + .06f * p
                        scaleX = scale
                        scaleY = scale
                    },
            )
            // 上面这张：跟着手指走，走的时候轻微后仰、缩小一点
            DeckPage(
                tab = tab,
                content = contentOf(tab),
                position = index + 1,
                total = deckTabs.size,
                onPlay = { contentOf(tab).songs.let { songs -> if (songs.isNotEmpty()) onPlay(songs, 0, false) } },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = (-slide.floatValue / size.height).coerceIn(0f, 1f)
                        translationY = slide.floatValue
                        rotationX = p * 10f
                        val scale = 1f - .03f * p
                        scaleX = scale
                        scaleY = scale
                        cameraDistance = 18f * density
                    }
                    .combinedClickable(
                        onClick = { open(tabState.value) },
                        onLongClick = {
                            haptics.tick(HapticArea.MENUS)
                            showSheet = true
                        },
                    ),
            )
        }
    }

    if (showSheet) {
        GlassBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("推荐设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "每天 6:00 由网易云按你的口味更新日推；风格、雷达、FM 在滑过去时取。向上滑卡片就是换下一张，长按卡片回到这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                DeckAction("刷新每日推荐", Icons.Rounded.Refresh) {
                    showSheet = false
                    tab = DeckTab.DAILY
                    retryDaily()
                }
                DeckAction("打开每日推荐列表", Icons.AutoMirrored.Rounded.PlaylistPlay) {
                    showSheet = false
                    onOpen(contentOf(DeckTab.DAILY).summary!!)
                }
                DeckAction("换成私人 FM", Icons.Rounded.Radio) {
                    showSheet = false
                    tab = DeckTab.FM
                }
                Spacer(Modifier.navigationBarsPadding().height(14.dp))
            }
        }
    }
}

/** 一张卡：背景（渐变 + 唱片沟槽 + 扇形封面）一层，文字与按钮一层。 */
@Composable
private fun DeckPage(
    tab: DeckTab,
    content: DeckContent,
    modifier: Modifier = Modifier,
    position: Int = 0,
    total: Int = 0,
    onPlay: () -> Unit = {},
) {
    val liquid = deckLiquidGlass()
    val source = rememberBackdropSource()
    val covers = remember(content.songs) { content.songs.mapNotNull { it.coverUrl }.distinct().take(3) }
    val playable = content.songs.isNotEmpty()

    Box(modifier.clip(RoundedCornerShape(24.dp))) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (liquid) Modifier.backdropSource(source) else Modifier)
                .background(Brush.linearGradient(listOf(tab.start, tab.mid, tab.end))),
        ) {
            DeckRings(Modifier.fillMaxSize())
            DeckArt(covers, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(112.dp))
        }
        Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 12.dp, top = 13.dp, bottom = 15.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        content.dateLine,
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = .55f),
                        letterSpacing = .3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (total > 0) {
                        Spacer(Modifier.width(8.dp))
                        DeckCounterChip(position, total, liquid, source)
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    tab.title,
                    fontSize = 25.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = .5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    content.subtitle,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = Color.White.copy(alpha = .66f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                PlayPill(playable, onPlay)
            }
            Spacer(Modifier.width(100.dp))
        }
    }
}

/** 页码小牌：开了液态玻璃就做成一块会折射背景的小玻璃，否则是一枚半透明深色胶囊。 */
@Composable
private fun DeckCounterChip(position: Int, total: Int, liquid: Boolean, source: BackdropSource) {
    val reduce = LocalReduceMotion.current
    val lift = rememberInfiniteTransition(label = "deck-lift").animateFloat(
        initialValue = 0f,
        targetValue = -2.2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "deck-arrow",
    )
    val style = remember {
        GlassStyle(
            cornerRadius = 11.dp,
            blur = 5.dp,
            tint = Color.White.copy(alpha = .16f),
            saturation = 1.5f,
            lens = Build.VERSION.SDK_INT >= 33,
            refractionHeight = 9.dp,
            refractionAmount = 7.dp,
            highlight = Color.White.copy(alpha = .55f),
            highlightWidth = .8.dp,
            fallback = Color.White.copy(alpha = .18f),
        )
    }
    Row(
        Modifier
            .height(22.dp)
            .then(if (liquid) Modifier.glass(source, style) else Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .22f)))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyledIcon(
            Icons.Rounded.KeyboardArrowUp,
            null,
            Modifier.size(12.dp).graphicsLayer {
                // 只在绘制阶段读动画值，不带着整个芯片每帧重组
                translationY = if (reduce) 0f else lift.value.dp.toPx()
            },
            tint = Color.White.copy(alpha = .78f),
        )
        Spacer(Modifier.width(1.dp))
        Text(
            "$position / $total",
            fontSize = 9.5.sp,
            color = Color.White.copy(alpha = .82f),
            fontWeight = FontWeight.Medium,
            letterSpacing = .2.sp,
        )
    }
}

/** 卡里的白色播放胶囊：按下去会轻微缩小。 */
@Composable
private fun PlayPill(enabled: Boolean, onPlay: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Row(
        Modifier
            .height(32.dp)
            .graphicsLayer {
                val scale = if (pressed) .94f else 1f
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) .96f else .32f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = {
                        haptics.tab(HapticArea.PLAYBACK)
                        onPlay()
                    },
                )
            }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyledIcon(Icons.Rounded.PlayArrow, null, Modifier.size(15.dp), tint = Color(0xFF1D1A12))
        Spacer(Modifier.width(5.dp))
        Text("播放", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D1A12))
    }
}

private fun artistLine(songs: List<NeteaseSong>): String =
    songs.flatMap { it.artists }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(3)
        .joinToString("、")
        .ifBlank { "今日精选" }

/** 唱片沟槽：以卡片右侧唱片为圆心的一圈圈细线。 */
@Composable
private fun DeckRings(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val step = 9.dp.toPx()
        val cx = size.width * .88f
        val cy = size.height * .48f
        val stroke = Stroke(1.dp.toPx())
        val ink = Color.White.copy(alpha = .055f)
        var radius = step
        while (radius < size.width) {
            drawCircle(ink, radius = radius, center = Offset(cx, cy), style = stroke)
            radius += step
        }
    }
}

/** 扇形三张封面 + 后面露出的唱片。 */
@Composable
private fun DeckArt(covers: List<String?>, modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 22.dp)
                .size(92.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF1C1C20), Color(0xFF0A0A0B)))),
        ) {
            Box(Modifier.align(Alignment.Center).size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = .05f)))
            Box(Modifier.align(Alignment.Center).size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .55f)))
        }
        val fan = remember {
            listOf(
                Triple(-12f, 0.dp, 12.dp),
                Triple(-2f, 16.dp, 2.dp),
                Triple(9f, 32.dp, 16.dp),
            )
        }
        fan.forEachIndexed { index, (rotation, dx, dy) ->
            val url = covers.getOrNull(index)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = dx, y = dy)
                    .size(72.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = .10f)),
            ) {
                if (url != null) UrlImage("$url?param=300y300", Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DeckAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyledIcon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

/** 私人 FM：一首一首往下听，垃圾桶换掉当前这首。 */
@Composable
private fun PersonalFmCard(client: NeteaseClient, onPlay: (List<NeteaseSong>, Int, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val tracks = remember { mutableStateListOf<NeteaseSong>() }
    var index by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme
    val reload: suspend () -> Unit = {
        loading = true
        error = null
        try {
            val next = client.personalFm()
            if (next.isEmpty()) {
                error = "私人 FM 暂时没有内容"
            } else {
                tracks.clear()
                tracks += next
                index = 0
            }
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "加载失败"
        }
        loading = false
    }
    LaunchedEffect(Unit) { if (tracks.isEmpty()) reload() }
    val current = tracks.getOrNull(index)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = .14f), colors.surfaceContainer.copy(alpha = .66f))))
            .clickable { if (current != null) onPlay(tracks.toList(), index, false) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (current != null) {
                UrlImage(current.coverUrl, Modifier.fillMaxSize())
            } else {
                StyledIcon(Icons.Rounded.Radio, null, Modifier.size(24.dp), tint = colors.primary)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("私人 FM", style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            val line = current?.title ?: error ?: if (loading) "正在为你挑选…" else "点一下开始"
            Text(
                line,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = current?.artist
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        GhostIconButton(
            Icons.Rounded.DeleteOutline,
            "不喜欢这首",
            {
                val song = current ?: return@GhostIconButton
                scope.launch {
                    runCatching { client.fmTrash(song.id) }
                    if (index + 1 < tracks.size) index++ else reload()
                }
            },
            enabled = current != null,
            size = 38.dp,
            iconSize = 19.dp,
        )
        GhostIconButton(
            Icons.Rounded.Refresh,
            "换一批",
            { scope.launch { reload() } },
            size = 38.dp,
            iconSize = 19.dp,
        )
    }
}

@Composable
private fun PodcastMiniCard(podcast: NeteaseClient.Podcast, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(6.dp)) {
        UrlImage(
            podcast.coverUrl,
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Text(podcast.name, Modifier.padding(start = 2.dp, top = 7.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(podcast.host.takeIf(String::isNotBlank), podcast.programCount.takeIf { it > 0 }?.let { "$it 期" }).joinToString(" · "),
            Modifier.padding(start = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


@Composable
private fun FlowChips(names: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        names.forEach { name ->
            val on = name == selected
            Text(
                name,
                Modifier.clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                    .clickable { onSelect(name) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun NeteaseCharts(client: NeteaseClient, bottomPadding: Dp, onOpen: (NeteasePlaylistSummary) -> Unit) {
    val (charts, retry) = rememberLoad(Unit) { client.charts() }
    val list = charts.value.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (list.isEmpty()) {
            if (charts.loading) skeletonCards(8)
            else item(span = { GridItemSpan(maxLineSpan) }, key = "state") { LoadingOrError(charts.loading, charts.error, retry) }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }, key = "preview-title") { SectionTitle("榜单速览") }
            item(span = { GridItemSpan(maxLineSpan) }, key = "preview") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list.take(6), key = { "pv:${it.id}" }) { chart ->
                        ChartPreviewCard(client, chart, { onOpen(chart) }, itemMotion())
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "all-title") { SectionTitle("全部榜单") }
            itemsIndexed(list, key = { _, it -> "c${it.id}" }) { _, chart -> NeteaseCoverCard(chart, { onOpen(chart) }, itemMotion()) }
        }
    }
}

/** 榜单卡：封面 + 榜名 + 更新频率 + 前三首。曲目按需加载，卡片不进入视口就不会请求。 */
@Composable
private fun ChartPreviewCard(client: NeteaseClient, chart: NeteasePlaylistSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (songs, retry) = rememberLoad(chart.id) { client.playlistSongs(chart.id) }
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.width(236.dp).height(172.dp).clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceContainer.copy(alpha = .72f))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UrlImage(
                chart.coverUrl?.let { "$it?param=200y200" },
                Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHighest),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(chart.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    chart.note.ifBlank { "实时更新" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val top = songs.value.orEmpty().take(3)
        when {
            top.isNotEmpty() -> top.forEachIndexed { index, song ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        Modifier.width(16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (index == 0) colors.primary else colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(song.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            songs.loading -> repeat(3) {
                Box(
                    Modifier.fillMaxWidth(if (it == 2) .6f else .9f).padding(vertical = 4.dp).height(10.dp)
                        .clip(RoundedCornerShape(5.dp)).background(colors.onSurface.copy(alpha = .07f)),
                )
            }
            else -> Text(
                "曲目加载失败，点击重试",
                Modifier.fillMaxWidth().clickable(onClick = retry),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}


@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun NeteaseSongRow(song: NeteaseSong, current: Boolean, mark: String? = null, onClick: () -> Unit) {
    val comments = LocalNeteaseComments.current
    val highlight by androidx.compose.animation.animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.Transparent,
        tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
        label = "row-current",
    )
    val titleInk by androidx.compose.animation.animateColorAsState(
        if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        tween(OlbyineTokens.Motion.standardMs, easing = OlbyineTokens.Motion.easeOut),
        label = "row-title",
    )
    var copyMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(highlight)
            .combinedClickable(onClick = onClick, onLongClick = { copyMenu = true })
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCopyMenu(
            expanded = copyMenu,
            onDismiss = { copyMenu = false },
            title = song.title,
            artist = song.artists.joinToString(" / "),
            album = song.album,
        )
        val playing = LocalIsPlaying.current()
        Box(Modifier.size(48.dp)) {
            UrlImage(song.coverUrl?.replace("600y600", "160y160"), Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            PlayingCoverScrim(current, playing, 10.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (mark.isNullOrBlank()) androidx.compose.ui.text.AnnotatedString(song.title) else highlightText(song.title, mark),
                style = MaterialTheme.typography.titleSmall,
                color = titleInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.unavailable) {
                    Badge("不可播放", filled = true)
                    Spacer(Modifier.width(5.dp))
                }
                val push = LocalNcmPush.current
                if (song.mvId > 0 && push != null) {
                    Box(Modifier.clip(RoundedCornerShape(5.dp)).clickable { push(NcmRoute.Mv(song.mvId)) }) {
                        Badge("MV", color = NeteaseRed)
                    }
                    Spacer(Modifier.width(5.dp))
                }
                Text(listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LocalPlayNextTrack.current?.let { next ->
            GhostIconButton(Icons.AutoMirrored.Rounded.PlaylistPlay, "下一首播放", { next(song.toTrack()) }, size = 40.dp, iconSize = 19.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (comments != null) {
            GhostIconButton(Icons.Rounded.ChatBubbleOutline, "评论", { comments(song.id, song.title) }, size = 40.dp, iconSize = 19.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val like = LocalNeteaseLike.current
        if (like != null) {
            val track = remember(song) { song.toTrack() }
            HeartWithMenu(
                track = { track },
                liked = like.isLiked(song.id),
                onToggle = { like.toggle(song.id) },
                iconSize = 20.dp,
                minSize = 40.dp,
            )
        }
    }
}

internal val LocalNeteaseProfile = staticCompositionLocalOf<((Long) -> Unit)?> { null }

/** 网易云曲目「喜欢」的状态与操作，由 OlbyineApp 统一提供。 */
internal class NeteaseLikeActions(
    val isLiked: (Long) -> Boolean,
    val toggle: (Long) -> Unit,
)

internal val LocalNeteaseLike = staticCompositionLocalOf<NeteaseLikeActions?> { null }

@Composable
internal fun NeteaseCommentsSheet(client: NeteaseClient, songId: Long, title: String, onDismiss: () -> Unit) {
    val hot = remember(songId) { mutableStateListOf<NeteaseComment>() }
    val latest = remember(songId) { mutableStateListOf<NeteaseComment>() }
    var total by remember(songId) { mutableStateOf(0) }
    var more by remember(songId) { mutableStateOf(true) }
    var loading by remember(songId) { mutableStateOf(false) }
    var error by remember(songId) { mutableStateOf<String?>(null) }
    var draft by remember(songId) { mutableStateOf("") }
    var replyTo by remember(songId) { mutableStateOf<NeteaseComment?>(null) }
    var thread by remember(songId) { mutableStateOf<NeteaseComment?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val openProfile = LocalNeteaseProfile.current
    val onUser: (NeteaseComment) -> Unit = { comment -> if (comment.userId > 0 && openProfile != null) { onDismiss(); openProfile(comment.userId) } }
    val loadMore: () -> Unit = {
        if (!loading && more) {
            loading = true
            error = null
            scope.launch {
                try {
                    val page = client.comments(songId, offset = latest.size, before = latest.lastOrNull()?.time ?: 0)
                    if (hot.isEmpty()) hot += page.hot
                    latest += page.latest.filter { new -> latest.none { it.id == new.id } }
                    total = page.total
                    more = page.more && page.latest.isNotEmpty()
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    error = failure.message ?: "评论加载失败"
                }
                loading = false
            }
        }
    }
    LaunchedEffect(songId) { loadMore() }
    val likedOverrides = remember(songId) { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    val toggleLike: (NeteaseComment) -> Unit = { comment ->
        if (!client.signedIn) {
            error = "登录网易云后可以点赞"
        } else {
            val next = !(likedOverrides[comment.id] ?: comment.liked)
            likedOverrides[comment.id] = next
            scope.launch {
                runCatching { client.likeComment(songId, comment.id, next) }
                    .onFailure {
                        likedOverrides[comment.id] = !next
                        error = it.message ?: "操作失败"
                    }
            }
        }
    }
    val replies = remember(thread?.id) { mutableStateListOf<NeteaseComment>() }
    var replyCursor by remember(thread?.id) { mutableStateOf<Long?>(-1L) }
    var repliesLoading by remember(thread?.id) { mutableStateOf(false) }
    val loadReplies: () -> Unit = {
        val parent = thread
        val cursor = replyCursor
        if (parent != null && cursor != null && !repliesLoading) {
            repliesLoading = true
            scope.launch {
                runCatching { client.commentReplies(songId, parent.id, cursor) }
                    .onSuccess { (page, next) -> replies += page.filter { new -> replies.none { it.id == new.id } }; replyCursor = next }
                    .onFailure { error = it.message ?: "回复加载失败"; replyCursor = null }
                repliesLoading = false
            }
        }
    }
    LaunchedEffect(thread?.id) { if (thread != null) loadReplies() }
    val sheetHeight = (LocalConfiguration.current.screenHeightDp * .86f).dp
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val atTop by remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 2
        }
    }
    GlassBottomSheet(onDismissRequest = onDismiss, dismissBlocked = !atTop) {
        Column(Modifier.fillMaxWidth().height(sheetHeight).imePadding()) {
            val parent = thread
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (parent != null) {
                    GhostIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回评论", { thread = null; replyTo = null }, size = 40.dp, iconSize = 20.dp)
                    Spacer(Modifier.width(4.dp))
                }
                Column(Modifier.padding(start = if (parent == null) 8.dp else 0.dp)) {
                    SwapContent(if (parent != null) "回复 · ${parent.replyCount}" else "评论" + if (total > 0) " · $total" else "") { heading ->
                        Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                if (parent != null) {
                    item(key = "parent") { CommentRow(parent, onUser = onUser, onReply = { replyTo = it }, onReplies = null, modifier = itemMotion(), liked = likedOverrides[parent.id] ?: parent.liked, onLike = toggleLike) }
                    item(key = "parent-line") { Overline("全部回复", itemMotion().padding(start = 6.dp, top = 8.dp, bottom = 4.dp)) }
                    itemsIndexed(replies, key = { _, it -> "r${it.id}" }) { index, reply ->
                        if (index >= replies.size - 4) LaunchedEffect(replies.size) { loadReplies() }
                        CommentRow(reply, onUser = onUser, onReply = { replyTo = it }, onReplies = null, modifier = itemMotion(), liked = likedOverrides[reply.id] ?: reply.liked, onLike = toggleLike)
                    }
                    if (repliesLoading && replies.isEmpty()) skeletonRows(4, avatar = true)
                    else if (repliesLoading) item(key = "replies-state") { LoadingOrError(true, null, {}, itemMotion()) }
                } else {
                    if (hot.isNotEmpty()) {
                        item(key = "hot") { Overline("精彩评论", itemMotion().padding(start = 6.dp, top = 8.dp, bottom = 4.dp)) }
                        itemsIndexed(hot, key = { index, it -> "h$index:${it.id}" }) { _, comment ->
                            CommentRow(comment, onUser = onUser, onReply = { c -> replyTo = c }, onReplies = { c -> thread = c }, modifier = itemMotion(), liked = likedOverrides[comment.id] ?: comment.liked, onLike = toggleLike)
                        }
                    }
                    if (latest.isNotEmpty()) {
                        item(key = "latest") { Overline("最新评论", itemMotion().padding(start = 6.dp, top = 14.dp, bottom = 4.dp)) }
                        itemsIndexed(latest, key = { _, it -> "l${it.id}" }) { index, comment ->
                            if (index >= latest.size - 5) LaunchedEffect(latest.size) { loadMore() }
                            CommentRow(comment, onUser = onUser, onReply = { replyTo = it }, onReplies = { thread = it }, modifier = itemMotion(), liked = likedOverrides[comment.id] ?: comment.liked, onLike = toggleLike)
                        }
                    }
                    if (loading && hot.isEmpty() && latest.isEmpty()) skeletonRows(6, avatar = true)
                    else if (loading || error != null) item(key = "state") { LoadingOrError(loading, error, loadMore, itemMotion()) }
                }
            }
            val target = replyTo
            val lastTarget = remember { arrayOfNulls<NeteaseComment>(1) }
            if (target != null) lastTarget[0] = target
            androidx.compose.animation.AnimatedVisibility(
                visible = target != null,
                enter = androidx.compose.animation.fadeIn(tween(160, easing = OlbyineTokens.Motion.easeOut)) +
                    androidx.compose.animation.expandVertically(tween(200, easing = OlbyineTokens.Motion.easeOut)),
                exit = androidx.compose.animation.fadeOut(tween(100)) + androidx.compose.animation.shrinkVertically(tween(160, easing = OlbyineTokens.Motion.easeOut)),
            ) {
                val shown = target ?: lastTarget[0]
                Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("回复 @${shown?.user.orEmpty()}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text("取消", Modifier.clip(CircleShape).clickable { replyTo = null }.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp).navigationBarsPadding().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OlbyineTextField(draft, { draft = it }, when {
                        !client.signedIn -> "登录网易云后可以发表评论"
                        target != null -> "回复 ${target.user}…"
                        else -> "说点什么…"
                    })
                }
                Spacer(Modifier.width(8.dp))
                OlbyineIconButton(
                    Icons.AutoMirrored.Rounded.Send,
                    "发送",
                    {
                        val text = draft.trim()
                        if (text.isNotEmpty() && !sending) {
                            sending = true
                            scope.launch {
                                try {
                                    if (target != null) client.replyComment(songId, target.id, text) else client.postComment(songId, text)
                                    haptics.confirm(HapticArea.MENUS)
                                    val posted = System.currentTimeMillis()
                                    val me = client.account.value
                                    val mine = NeteaseComment(
                                        id = posted,
                                        user = me?.nickname?.takeIf(String::isNotBlank) ?: "我",
                                        userId = me?.userId ?: 0L,
                                        avatarUrl = me?.avatarUrl?.let { url -> if (url.contains("?")) url else "$url?param=100y100" },
                                        content = text,
                                        time = posted,
                                        likedCount = 0,
                                        replyTo = target?.let { "@${it.user}：${it.content}" },
                                    )
                                    if (thread != null) replies += mine else {
                                        latest.add(0, mine)
                                        total += 1
                                    }
                                    draft = ""
                                    replyTo = null
                                } catch (failure: Throwable) {
                                    if (failure is CancellationException) throw failure
                                    error = failure.message ?: "发送失败"
                                }
                                sending = false
                            }
                        }
                    },
                    size = 46.dp,
                    container = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    enabled = client.signedIn && draft.isNotBlank() && !sending,
                )
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

@Composable
private fun CommentRow(
    comment: NeteaseComment,
    onUser: (NeteaseComment) -> Unit,
    onReply: (NeteaseComment) -> Unit,
    onReplies: ((NeteaseComment) -> Unit)?,
    modifier: Modifier = Modifier,
    liked: Boolean = comment.liked,
    onLike: ((NeteaseComment) -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onReply(comment) }.padding(horizontal = 6.dp, vertical = 8.dp)) {
        UrlImage(
            comment.avatarUrl,
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onUser(comment) },
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                comment.user,
                Modifier.clip(RoundedCornerShape(6.dp)).clickable { onUser(comment) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(comment.content, style = MaterialTheme.typography.bodyMedium)
            if (comment.replyTo != null) {
                Text(
                    comment.replyTo,
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f)).padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(comment.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
                if (onReplies != null && comment.replyCount > 0) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "查看 ${comment.replyCount} 条回复 ›",
                        Modifier.clip(CircleShape).clickable { onReplies(comment) }.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                val count = comment.likedCount + if (liked && !comment.liked) 1 else if (!liked && comment.liked) -1 else 0
                Row(
                    Modifier
                        .clip(CircleShape)
                        .then(if (onLike != null) Modifier.clickable { onLike(comment) } else Modifier)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StyledIcon(
                        Icons.Rounded.ThumbUp,
                        "点赞",
                        Modifier.size(13.dp),
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .8f),
                    )
                    if (count > 0) {
                        Spacer(Modifier.width(3.dp))
                        Text(
                            playCountLabel(count.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NeteaseMessages(client: NeteaseClient, signedIn: Boolean, bottomPadding: Dp, onLogin: () -> Unit) {
    val openChat = LocalNeteaseChat.current
    if (!signedIn) {
        EmptyState(Icons.Rounded.Mail, "登录后查看私信", Modifier.fillMaxSize(), message = "私信需要登录网易云音乐") {
            PillButton("登录", onLogin)
        }
        return
    }

    val (threads, retry) = rememberLoad(Unit) { client.messageThreads() }
    var opened by remember { mutableStateOf(setOf<Long>()) }
    val list = threads.value.orEmpty().map { if (it.userId in opened) it.copy(unread = 0) else it }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = bottomPadding + 24.dp)) {
        if (list.isEmpty()) {
            if (threads.loading) skeletonRows(7, avatar = true)
            else item(key = "state") {
                if (threads.error != null) LoadingOrError(false, threads.error, retry, itemMotion())
                else Text("还没有私信", itemMotion().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(list, key = { it.userId }) { item ->
            val openProfile = LocalNeteaseProfile.current
            val (presence, _) = rememberLoad(item.userId) { client.cachedUserStatus(item.userId) }
            Row(
                itemMotion().fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                    opened = opened + item.userId
                    client.markThreadRead(item.userId, item.time)
                    openChat?.invoke(item)
                }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    UrlImage(item.avatarUrl, Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { openProfile?.invoke(item.userId) })
                    if (presence.value != null) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background)
                                .padding(2.5.dp).clip(CircleShape).background(Color(0xFF34C759)),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.nickname,
                            Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (item.unread > 0) FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        presence.value?.let { status ->
                            Text(
                                " · $status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        item.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.unread > 0) MaterialTheme.colorScheme.onSurface.copy(alpha = .82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(dateFormat.format(Date(item.time)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.unread > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (item.unread > 99) "99+" else item.unread.toString(),
                            Modifier.clip(CircleShape).background(NeteaseRed.copy(alpha = .14f)).padding(horizontal = 7.dp, vertical = 1.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeteaseRed.copy(alpha = .9f),
                        )
                    }
                }
            }
        }
    }
}

internal val NeteaseLikedIcon = Icons.Rounded.Favorite
