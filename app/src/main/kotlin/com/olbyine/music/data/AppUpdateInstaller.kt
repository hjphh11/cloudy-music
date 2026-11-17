package com.olbyine.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.olbyine.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 应用内更新的下载状态。 */
sealed interface UpdateDownload {
    data object Idle : UpdateDownload

    data class Downloading(val progress: Float, val bytes: Long, val total: Long) : UpdateDownload

    data class Ready(val path: String) : UpdateDownload

    data class Failed(val message: String) : UpdateDownload
}

/**
 * 在 App 里把新版本 APK 下载到自己的目录，然后交给系统安装器。
 *
 * 之前是直接 ACTION_VIEW 打开浏览器，用户得在浏览器里再点一次下载、
 * 再从通知栏点安装，绕一圈。现在下载和安装都在 App 内完成。
 */
class AppUpdateInstaller(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }

    fun apkFile(version: String): File = File(dir, "Cloudy-$version.apk")

    /** 已经下载好的包，直接拿来装，不用重下一遍。 */
    fun existing(version: String): File? = apkFile(version).takeIf { it.isFile && it.length() > 1L shl 20 }

    suspend fun download(
        url: String,
        version: String,
        onProgress: (Float, Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = apkFile(version)
        val temp = File(dir, "${target.name}.part")
        if (temp.exists()) temp.delete()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Cloudy/${BuildConfig.VERSION_NAME}")
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("服务器返回 ${response.code}")
            val body = response.body ?: error("下载内容为空")
            val total = body.contentLength()
            var read = 0L
            temp.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        out.write(buffer, 0, count)
                        read += count
                        onProgress(if (total > 0) read.toFloat() / total else 0f, read, total)
                    }
                }
            }
        }
        // 断流时 OkHttp 不一定报错，长度对不上就直接判失败，别把半个包递给安装器
        if (temp.length() < 1L shl 20) {
            temp.delete()
            error("安装包不完整，请重试")
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.delete()
            error("写入安装包失败")
        }
        target
    }

    /** 8.0 起需要用户单独允许「安装未知应用」。 */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** 交给系统安装器。包在本应用私有目录里，所以必须走 FileProvider 授个临时读权限。 */
    fun install(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** 清掉旧版本的包，免得每次更新都在数据目录里留一份。 */
    fun purgeExcept(keep: String) {
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (!file.name.contains(keep, ignoreCase = true)) file.delete()
            }
        }
    }
}
