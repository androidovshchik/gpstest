package androidovshchik.gpstest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.gpstest.R
import com.android.gpstest.io.BaseFileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import java.io.File

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val ftpClient = FTPClient()

    private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)

    override suspend fun doWork(): Result {
        println("doWork")
        setForeground(createForegroundInfo())
        val folder = File(applicationContext.getExternalFilesDir(null), BaseFileLogger.FILE_PREFIX)
        val files = folder.listFiles().orEmpty()
        val jsonFiles = files.filter { it.canWrite() && it.name.endsWith(".json") }
            .sortedBy { it.name }
            .dropLast(1)
        println("jsonFiles size=${jsonFiles.size}")
        val txtFiles = files.filter { it.canWrite() && it.name.endsWith(".txt") }
            .sortedBy { it.name }
            .dropLast(1)
        println("txtFiles size=${txtFiles.size}")
        try {
            uploadFiles(jsonFiles.iterator())
            uploadFiles(txtFiles.iterator())
        } catch (ignored: Exception) {
        }
        return Result.success()
    }

    private suspend fun uploadFiles(iterator: Iterator<File>) {
        try {
            check(!prefs.getString("custom_ftp_url", null).isNullOrBlank()) {
                "Не задан адрес сервера FTP"
            }
            check(!prefs.getString("custom_ftp_login", null).isNullOrBlank()) {
                "Не задан логин FTP"
            }
            check(!prefs.getString("custom_ftp_password", null).isNullOrBlank()) {
                "Не задан пароль FTP"
            }
            check(!prefs.getString("custom_ftp_path", null).isNullOrBlank()) {
                "Не задан путь папки FTP"
            }

            ftpClient.connect(prefs.getString("custom_ftp_url", null))
            ftpClient.login(
                prefs.getString("custom_ftp_login", null),
                prefs.getString("custom_ftp_password", null)
            )

            ftpClient.enterLocalPassiveMode()
            ftpClient.changeWorkingDirectory(prefs.getString("custom_ftp_path", null))

            while (iterator.hasNext()) {
                val file = iterator.next()
                println("uploading ${file.path}")
                file.inputStream().use { inputStream ->
                    ftpClient.storeFile(file.name, inputStream)
                }
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG)
                    .show()
            }
            if (e is IllegalStateException) {
                throw e
            }
        } finally {
            ftpClient.logout()
            ftpClient.disconnect()
        }
        if (iterator.hasNext()) {
            delay(1500L)
            uploadFiles(iterator)
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel("custom", "Custom", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, "custom")
            .setContentTitle("Загрузка файлов FTP")
            .setSmallIcon(R.drawable.baseline_cloud_upload_24)
            .setOngoing(true)
            .build()
        return ForegroundInfo(101_010_101, notification)
    }

    companion object {

        private const val NAME = "custom_upload"

        fun launch(context: Context) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (!prefs.getBoolean("custom_enable_upload", false)) {
                return
            }
            println("launch UploadWorker")
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .build()
            with(WorkManager.getInstance(context)) {
                enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
            }
        }

        fun cancel(context: Context) {
            with(WorkManager.getInstance(context)) {
                cancelUniqueWork(NAME)
            }
        }
    }
}