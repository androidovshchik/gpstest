package androidovshchik.gpstest

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn

sealed interface InternetConnectionState {
    object InternetConnectionAvailableState : InternetConnectionState
    object InternetConnectionLostState : InternetConnectionState
}

interface NetworkStatus {
    fun getNetworkStatus(): StateFlow<InternetConnectionState>
    fun setNetworkStatus(status: InternetConnectionState)
}

@Suppress("DEPRECATION")
fun isConnectedToInternet(context: Context): Boolean {
    try {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return false
}

@OptIn(DelicateCoroutinesApi::class)
class AndroidNetworkStatus(context: Context) : NetworkStatus {

    private val networkStatus: MutableStateFlow<InternetConnectionState> = MutableStateFlow(
        if (isConnectedToInternet(context)) {
            InternetConnectionState.InternetConnectionAvailableState
        } else {
            InternetConnectionState.InternetConnectionLostState
        }
    )

    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    init {
        networkStatus.launchIn(GlobalScope)
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        connectivityManager?.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    networkStatus.value = InternetConnectionState.InternetConnectionAvailableState
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    networkStatus.value = InternetConnectionState.InternetConnectionLostState
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    networkStatus.value = InternetConnectionState.InternetConnectionLostState
                }
            })
    }

    override fun getNetworkStatus(): StateFlow<InternetConnectionState> =
        networkStatus.asStateFlow()

    override fun setNetworkStatus(status: InternetConnectionState) {
        networkStatus.value = status
    }
}