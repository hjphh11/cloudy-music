package com.olbyine.music

import android.app.Application
import com.olbyine.music.data.AppleMusicClient
import com.olbyine.music.data.MusicLibraryRepository
import com.olbyine.music.data.OnlineLyricsRepository
import com.olbyine.music.data.OnlineMetadataRepository
import com.olbyine.music.data.SettingsRepository
import com.olbyine.music.playback.PlaybackConnection
import kotlinx.coroutines.launch

class OlbyineApplication : Application() {
    companion object {
        var artworkCacheDir: java.io.File? = null
            private set
    }

    lateinit var musicLibrary: MusicLibraryRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var appleMusic: AppleMusicClient
        private set
    lateinit var onlineLyrics: OnlineLyricsRepository
        private set
    lateinit var playback: PlaybackConnection
        private set
    lateinit var onlineMetadata: OnlineMetadataRepository
        private set
    lateinit var netease: com.olbyine.music.data.NeteaseClient
        private set
    lateinit var lyricsHub: com.olbyine.music.data.LyricsHub
        private set
    lateinit var webDav: com.olbyine.music.data.WebDavRepository
        private set
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val updateChecker by lazy { com.olbyine.music.data.UpdateChecker() }
    val neteaseDownloader by lazy { com.olbyine.music.data.NeteaseDownloader(this) }
    val downloadRecords by lazy { com.olbyine.music.data.DownloadRecords(this) }
    val downloadNotifier by lazy { com.olbyine.music.data.DownloadNotifier(this) }
    val backup by lazy { com.olbyine.music.data.BackupRepository(this) }

    @get:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val motionCache: androidx.media3.datasource.cache.SimpleCache by lazy {
        androidx.media3.datasource.cache.SimpleCache(
            java.io.File(cacheDir, "motion"),
            androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
            androidx.media3.database.StandaloneDatabaseProvider(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        artworkCacheDir = java.io.File(cacheDir, "remote-art").apply { mkdirs() }
        if (Application.getProcessName() != packageName) return
        musicLibrary = MusicLibraryRepository(this)
        settings = SettingsRepository(this)
        appleMusic = AppleMusicClient(this)
        onlineLyrics = OnlineLyricsRepository(appleMusic)
        playback = PlaybackConnection(this)
        onlineMetadata = OnlineMetadataRepository(this, appleMusic)
        netease = com.olbyine.music.data.NeteaseClient(this)
        lyricsHub = com.olbyine.music.data.LyricsHub(musicLibrary, netease, onlineLyrics, appScope)
        webDav = com.olbyine.music.data.WebDavRepository(this)
        musicLibrary.remoteHeaders = webDav::headersFor
        appScope.launch { runCatching { musicLibrary.dropStoredCovers() } }
        com.olbyine.music.playback.usb.UsbAudioDriver.init(this)
        com.olbyine.music.data.ListenTogetherIm.init(this)
    }
}
