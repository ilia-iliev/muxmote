package dev.muxmote.net

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.net.toUri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE"

/** 100.64.0.0/10 or fd7a:115c:a1e0::/48 */
fun isTailscaleAddress(address: InetAddress): Boolean {
  val b = address.address
  return when (address) {
    is Inet4Address -> b[0] == 100.toByte() && (b[1].toInt() and 0xC0) == 64
    is Inet6Address -> b.take(6) == listOf(0xfd, 0x7a, 0x11, 0x5c, 0xa1, 0xe0).map { it.toByte() }
    else -> false
  }
}

/** Only VPN addresses count: carriers use 100.64/10 for CGNAT too. */
fun tailnetUp(vpnAddresses: Collection<List<InetAddress>>) = vpnAddresses.any { it.any(::isTailscaleAddress) }

/**
 * Tracks whether a VPN holding a tailnet address is up. Apps get only the VPNs that apply to them,
 * and their link properties (addresses included) are not redacted for non-owners.
 */
class Tailscale(private val context: Context) {
  private val vpns = ConcurrentHashMap<Network, List<InetAddress>>()
  private val state = MutableStateFlow(false)
  val connected: StateFlow<Boolean> = state

  init {
    val vpn = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
    context
      .getSystemService(ConnectivityManager::class.java)
      .registerNetworkCallback(
        vpn,
        object : ConnectivityManager.NetworkCallback() {
          // Always follows onAvailable, so it also reports new networks.
          override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            vpns[network] = linkProperties.linkAddresses.map { it.address }
            refresh()
          }

          override fun onLost(network: Network) {
            vpns.remove(network)
            refresh()
          }
        },
      )
  }

  fun refresh() {
    state.value = tailnetUp(vpns.values)
  }

  /** Opens Tailscale, or its store page when it isn't installed. */
  fun openApp() {
    val intent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE) ?: Intent(Intent.ACTION_VIEW, PLAY_STORE_URL.toUri())
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
