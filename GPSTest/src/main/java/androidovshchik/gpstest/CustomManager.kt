package androidovshchik.gpstest

import android.content.Context
import android.content.ContextWrapper
import android.os.FileObserver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import com.android.gpstest.io.BaseFileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

class CustomManager(
    context: Context,
    private val manualShare: () -> Unit
) : ContextWrapper(context), DefaultLifecycleObserver, CoroutineScope {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private var fileObserver: FileObserver? = null

    private val networkStatus = AndroidNetworkStatus(context)

    private var networkJob: Job? = null

    private var timerJob: Job? = null

    private val parentJob = SupervisorJob()

    override val coroutineContext = Dispatchers.Main + parentJob

    @Suppress("DEPRECATION")
    override fun onCreate(owner: LifecycleOwner) {
        println("onCreate")
        val folder = File(getExternalFilesDir(null), BaseFileLogger.FILE_PREFIX)
        fileObserver = object : FileObserver(folder.path) {

            override fun onEvent(event: Int, path: String?) {
                when (event) {
                    ACCESS, MODIFY, CLOSE_NOWRITE, OPEN, MOVED_FROM, MOVED_TO, DELETE, DELETE_SELF,
                    MOVE_SELF -> {}
                    else -> {
                        // ATTRIB, CLOSE_WRITE, CREATE etc.
                        println("onEvent ${getEventName(event)} $path")
                        UploadWorker.launch(applicationContext)
                    }
                }
            }
        }
        setupWork()
    }

    fun onPrefChange(key: String) {
        if (key == "custom_enable_upload" || key == "custom_share_interval") {
            setupWork()
        }
    }

    private fun setupWork() {
        if (prefs.getBoolean("custom_enable_upload", false)) {
            println("setupWork start")
            fileObserver?.startWatching()
            networkJob?.cancel()
            networkJob = launch {
                networkStatus.getNetworkStatus().collect {
                    println("getNetworkStatus $it")
                    if (it == InternetConnectionState.InternetConnectionAvailableState) {
                        UploadWorker.launch(applicationContext)
                    }
                }
            }
            timerJob?.cancel()
            startTimer()
        } else {
            println("setupWork stop")
            fileObserver?.stopWatching()
            networkJob?.cancel()
            UploadWorker.cancel(applicationContext)
            timerJob?.cancel()
        }
    }

    private fun startTimer() {
        val minutes = prefs.getString("custom_share_interval", null)
            ?.trim()
            ?.toLongOrNull()
            ?: 60L
        val millis = TimeUnit.MINUTES.toMillis(minutes)
        val lastTimer = prefs.getLong("custom_last_timer", 0L)
        val now = System.currentTimeMillis()
        val diff = now - lastTimer
        if (diff < millis) {
            println("timer manual share diff=${Duration.ofMillis(diff)} delay=${Duration.ofMillis(millis - diff)}")
        } else {
            println("timer manual share")
        }
        timerJob = launch {
            while (true) {
                if (diff < millis) {
                    delay(millis - diff)
                }
                println("iteration manual share")
                manualShare()
                prefs.edit()
                    .putLong("custom_last_timer", System.currentTimeMillis())
                    .apply()
                delay(millis)
            }
        }
    }

    private fun getEventName(event: Int): String {
        return when (event) {
            FileObserver.ACCESS -> "ACCESS"
            FileObserver.MODIFY -> "MODIFY"
            FileObserver.ATTRIB -> "ATTRIB"
            FileObserver.CLOSE_WRITE -> "CLOSE_WRITE"
            FileObserver.CLOSE_NOWRITE -> "CLOSE_NOWRITE"
            FileObserver.OPEN -> "OPEN"
            FileObserver.MOVED_FROM -> "MOVED_FROM"
            FileObserver.MOVED_TO -> "MOVED_TO"
            FileObserver.CREATE -> "CREATE"
            FileObserver.DELETE -> "DELETE"
            FileObserver.DELETE_SELF -> "DELETE_SELF"
            FileObserver.MOVE_SELF -> "MOVE_SELF"
            else -> "UNKNOWN ($event)"
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        fileObserver?.stopWatching()
        fileObserver = null
        parentJob.cancelChildren()
    }
}