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
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.util.Properties

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)

    private var ftpClient: FTPClient? = null

    private var jsch: JSch? = null

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
            when (val type = prefs.getString("custom_ftp_type", "FTP")) {
                "FTP", "FTPS" -> {
                    ftpClient = if (type == "FTP") FTPClient() else FTPSClient()
                    uploadFilesFTP(jsonFiles.iterator())
                    uploadFilesFTP(txtFiles.iterator())
                }
                else -> {
                    jsch = JSch()
                    uploadFilesSFTP(jsonFiles.iterator())
                    uploadFilesSFTP(txtFiles.iterator())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG)
                    .show()
            }
        }
        return Result.success()
    }

    private suspend fun uploadFilesFTP(iterator: Iterator<File>) {
        try {
            ftpClient?.connect(prefs.getString("custom_ftp_url", null))
            ftpClient?.login(
                prefs.getString("custom_ftp_login", null),
                prefs.getString("custom_ftp_password", null)
            )
            ftpClient?.enterLocalPassiveMode()
            ftpClient?.changeWorkingDirectory(prefs.getString("custom_ftp_path", null))

            while (iterator.hasNext()) {
                val file = iterator.next()
                println("uploading ${ftpClient is FTPSClient} ${file.path}")
                file.inputStream().use { inputStream ->
                    ftpClient?.storeFile(file.name, inputStream)
                }
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG)
                    .show()
            }
        } finally {
            ftpClient?.logout()
            ftpClient?.disconnect()
        }
        if (iterator.hasNext()) {
            delay(1500L)
            uploadFilesFTP(iterator)
        }
    }

    private suspend fun uploadFilesSFTP(iterator: Iterator<File>) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = jsch?.getSession(
                prefs.getString("custom_ftp_login", null),
                prefs.getString("custom_ftp_url", null)
            )
            session?.setPassword(prefs.getString("custom_ftp_password", null))
            session?.setConfig(Properties().apply {
                put("StrictHostKeyChecking", "no")
            })
            session?.connect()
            channel = session?.openChannel("sftp") as? ChannelSftp
            channel?.connect()
            channel?.cd(prefs.getString("custom_ftp_path", null))

            while (iterator.hasNext()) {
                val file = iterator.next()
                println("uploading sftp ${file.path}")
                file.inputStream().use { inputStream ->
                    channel?.put(inputStream, file.name)
                }
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG)
                    .show()
            }
        } finally {
            channel?.exit()
            session?.disconnect()
        }
        if (iterator.hasNext()) {
            delay(1500L)
            uploadFilesSFTP(iterator)
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