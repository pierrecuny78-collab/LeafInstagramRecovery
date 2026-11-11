package com.leafvibe.leafinstagramrecovery

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Local-only rolling snapshot of app-private data.
 * Excludes caches, WebView sessions, no_backup and previous snapshots.
 */
class LeafAutoBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val dataRoot = File(applicationContext.applicationInfo.dataDir)
        val backupDir = File(applicationContext.filesDir, "leaf_backups")
        if (!backupDir.exists() && !backupDir.mkdirs()) return Result.retry()
        if (backupDir.usableSpace < 2L * 1024L * 1024L) return Result.retry()

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(backupDir, "leaf-backup-$stamp.zip")
        val temporary = File(backupDir, "leaf-backup-$stamp.tmp")
        val manifest = JSONArray()
        var totalBytes = 0L
        val maxFileBytes = 256L * 1024L * 1024L

        ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { zip ->
            dataRoot.walkTopDown()
                .onEnter { directory ->
                    val relative = directory.relativeTo(dataRoot).invariantSeparatorsPath
                    relative.isEmpty() || relative.split("/").none { it in EXCLUDED_DIRS }
                }
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(dataRoot).invariantSeparatorsPath
                    if (relative.split("/").any { it in EXCLUDED_DIRS }) return@forEach
                    if (file.length() > maxFileBytes) return@forEach
                    val digest = MessageDigest.getInstance("SHA-256")
                    zip.putNextEntry(ZipEntry(relative))
                    BufferedInputStream(FileInputStream(file)).use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                            zip.write(buffer, 0, count)
                            totalBytes += count
                        }
                    }
                    zip.closeEntry()
                    manifest.put(JSONObject().apply {
                        put("path", relative)
                        put("bytes", file.length())
                        put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
                    })
                }
            zip.putNextEntry(ZipEntry("leaf-backup-manifest.json"))
            zip.write(JSONObject().apply {
                put("format", 1)
                put("package", applicationContext.packageName)
                put("createdAt", System.currentTimeMillis())
                put("totalBytes", totalBytes)
                put("files", manifest)
            }.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        if (!temporary.renameTo(target)) {
            temporary.delete()
            return Result.retry()
        }
        backupDir.listFiles()
            ?.filter { it.name.startsWith("leaf-backup-") && it.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_BACKUPS)
            ?.forEach { it.delete() }
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val KEEP_BACKUPS = 7
        private val EXCLUDED_DIRS = setOf(
            "cache", "code_cache", "no_backup", "app_webview", "leaf_backups"
        )
    }
}

object LeafAutoBackupScheduler {
    private const val PERIODIC_NAME = "leaf_auto_backup_daily"
    private const val SOON_NAME = "leaf_auto_backup_after_session"

    fun install(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<LeafAutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun requestSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<LeafAutoBackupWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            SOON_NAME, ExistingWorkPolicy.REPLACE, request
        )
    }
}
