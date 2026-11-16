package com.olbyine.music.data

import com.olbyine.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
)

/**
 * Cloudy 自己的更新检查。
 *
 * 不再去问上游仓库的 release，而是读对象存储上的一份静态清单
 * （[MANIFEST_URL]），发版时只改清单、不用动客户端。
 * 版本判定优先用清单里的 versionCode，缺省时退回版本号比较。
 */
class UpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(MANIFEST_URL)
            .header("Accept", "application/json")
            .header("User-Agent", "Cloudy/${BuildConfig.VERSION_NAME}")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty().ifBlank { return@withContext null }
            val json = JSONObject(body)
            val version = json.optString("versionName").removePrefix("v").removePrefix("V").trim()
            if (version.isBlank()) return@withContext null
            val code = json.optInt("versionCode", 0)
            val newer = if (code > 0) code > BuildConfig.VERSION_CODE else isNewer(version, BuildConfig.VERSION_NAME)
            if (!newer) return@withContext null
            AppUpdate(
                version = version,
                notes = plainNotes(json.optString("notes")),
                pageUrl = json.optString("pageUrl").ifBlank { PAGE_URL },
                apkUrl = json.optString("apkUrl")
                    .trim()
                    .takeIf { it.endsWith(".apk", ignoreCase = true) },
            )
        }
    }

    companion object {
        /** 版本清单。发版时改这一份 JSON 即可。 */
        const val MANIFEST_URL = "https://cn-nb1.rains3.com/qingwu/cloudy/version.json"

        /** 清单缺失时的兜底下载页。 */
        const val PAGE_URL = "https://cn-nb1.rains3.com/qingwu/cloudy/index.html"

        fun isNewer(candidate: String, current: String): Boolean {
            val left = parts(candidate)
            val right = parts(current)
            for (index in 0 until maxOf(left.size, right.size)) {
                val a = left.getOrElse(index) { 0 }
                val b = right.getOrElse(index) { 0 }
                if (a != b) return a > b
            }
            return false
        }

        private fun parts(version: String): List<Int> =
            version.split('.', '-', '+', '_', ' ')
                .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
                .dropLastWhile { it == 0 }

        /**
         * 弹窗是纯文本渲染，清单里就算写了 Markdown 也要把标记去掉，
         * 否则用户看到的是「### USB 独占与音频输出」这种原始符号。
         */
        internal fun plainNotes(raw: String): String = raw
            .lineSequence()
            .map { line ->
                var text = line.trim()
                text = text.removePrefix("######").removePrefix("#####").removePrefix("####")
                    .removePrefix("###").removePrefix("##").removePrefix("#")
                text = text.trimStart('-', '*', '+', '•').trim()
                text = text.replace("**", "").replace("__", "").replace("`", "")
                text
            }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }
}
