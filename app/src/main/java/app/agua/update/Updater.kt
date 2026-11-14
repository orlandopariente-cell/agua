package app.agua.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.agua.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val title: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * Checks the latest GitHub release of [BuildConfig.UPDATE_REPO].
 * Releases are tagged "v<versionCode>" (e.g. v5) and carry the APK as an asset.
 */
object Updater {
    val isConfigured: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    /** Returns the newer release, or null when already up to date. Throws IOException on network errors. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            when (conn.responseCode) {
                200 -> Unit
                404 -> return@withContext null // no release published yet
                else -> throw IOException("GitHub respondeu ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = json.getString("tag_name").removePrefix("v").toIntOrNull()
                ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = json.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    break
                }
            }
            UpdateInfo(
                versionCode = code,
                title = json.optString("name").ifBlank { "Versão 1.$code" },
                notes = json.optString("body"),
                apkUrl = apkUrl ?: return@withContext null,
            )
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, "agua-${info.versionCode}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode != 200) throw IOException("Download falhou (${conn.responseCode})")
            conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        file
    }

    /** Android asks once per app for permission to install APKs. */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
