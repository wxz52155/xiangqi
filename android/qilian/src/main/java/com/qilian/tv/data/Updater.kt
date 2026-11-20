package com.qilian.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.qilian.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 在线更新：每次发布时 GitHub Actions 会在 Release 里附带 update.json，
 * 应用通过 gh-proxy 加速地址读取它，发现新版本后下载 APK 并调起系统安装。
 */
object Updater {
    /** 下载加速：在 GitHub 地址前加上这个前缀。 */
    const val PROXY = "https://gh-proxy.org/"

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val sha256: String,
        val size: Long,
        val publishedAt: String,
    ) {
        val proxiedApkUrl get() = if (apkUrl.startsWith(PROXY)) apkUrl else PROXY + apkUrl
    }

    data class Installed(val versionName: String, val versionCode: Long)

    fun installed(context: Context): Installed = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        Installed(info.versionName ?: "?", code)
    }.getOrDefault(Installed("?", 0))

    private val manifestUrl: String
        get() = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest/download/update.json"

    /** 读取最新版本信息；先走加速地址，失败再直连 GitHub。 */
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in listOf(PROXY + manifestUrl, manifestUrl)) {
            try {
                return@withContext parse(fetchText(url))
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw IllegalStateException("无法获取更新信息：${lastError?.message ?: "网络异常"}")
    }

    fun isNewer(release: Release, installed: Installed) = release.versionCode > installed.versionCode

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NanpoXiangqi")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(text: String): Release {
        val o = JSONObject(text)
        return Release(
            versionCode = o.getInt("versionCode"),
            versionName = o.optString("versionName"),
            notes = o.optString("notes"),
            apkUrl = o.getString("apkUrl"),
            sha256 = o.optString("sha256"),
            size = o.optLong("size"),
            publishedAt = o.optString("publishedAt"),
        )
    }

    /** 下载 APK 到缓存目录，回调进度（0..1）。校验 SHA-256。 */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "NanpoXiangqi-${release.versionName}.apk")
        var lastError: Throwable? = null
        for (url in listOf(release.proxiedApkUrl, release.apkUrl)) {
            try {
                downloadTo(url, file, release.size, onProgress)
                if (release.sha256.isNotBlank() && !sha256(file).equals(release.sha256, ignoreCase = true)) {
                    throw IllegalStateException("安装包校验失败")
                }
                return@withContext file
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                file.delete()
            }
        }
        throw IllegalStateException("下载失败：${lastError?.message ?: "网络异常"}")
    }

    private suspend fun downloadTo(url: String, file: File, expected: Long, onProgress: (Float) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NanpoXiangqi")
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = conn.contentLength.toLong().takeIf { it > 0 } ?: expected
            var read = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (expected > 0 && read != expected) throw IllegalStateException("下载不完整")
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Android 8+ 需要“安装未知应用”授权。 */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 调起系统安装界面。 */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
