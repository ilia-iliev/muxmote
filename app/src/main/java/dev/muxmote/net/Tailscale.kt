package dev.muxmote.net

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

/** 100.64.0.0/10 or fd7a:115c:a1e0::/48 */
fun isTailscaleAddress(address: InetAddress): Boolean {
  val b = address.address
  return when (address) {
    is Inet4Address -> b[0] == 100.toByte() && (b[1].toInt() and 0xC0) == 64
    is Inet6Address -> b.take(6) == listOf(0xfd, 0x7a, 0x11, 0x5c, 0xa1, 0xe0).map { it.toByte() }
    else -> false
  }
}

/** Tracks whether a VPN holding a tailnet address is up. */
class Tailscale(private val context: Context) {
  private val connectivity = context.getSystemService(ConnectivityManager::class.java)
  private val state = MutableStateFlow(isConnected())
  val connected: StateFlow<Boolean> = state

  init {
    val vpn = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
    connectivity.registerNetworkCallback(
      vpn,
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()

        override fun onLost(network: Network) = refresh()

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refresh()
      },
    )
  }

  fun refresh() {
    state.value = isConnected()
  }

  fun openApp() {
    val intent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE) ?: return
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  @Suppress("DEPRECATION") // allNetworks is the only way to see a VPN that is not the default network.
  private fun isConnected() =
    connectivity.allNetworks.any { network ->
      val vpn = connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
      vpn && connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().any { isTailscaleAddress(it.address) }
    }
}
