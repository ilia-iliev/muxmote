package dev.muxmote.net

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE"
private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")

/** 100.64.0.0/10 or fd7a:115c:a1e0::/48 */
fun isTailscaleAddress(address: InetAddress): Boolean {
  val b = address.address
  return when (address) {
    is Inet4Address -> b[0] == 100.toByte() && (b[1].toInt() and 0xC0) == 64
    is Inet6Address -> b.take(6) == listOf(0xfd, 0x7a, 0x11, 0x5c, 0xa1, 0xe0).map { it.toByte() }
    else -> false
  }
}

/** The VPN holding a tailnet address. Only VPNs count: carriers use 100.64/10 for CGNAT too. */
fun <N> tailnet(vpns: Map<N, List<InetAddress>>): N? = vpns.entries.firstOrNull { (_, addresses) -> addresses.any(::isTailscaleAddress) }?.key

/**
 * Whether [host] may be reached only through the tailnet. Literal private-LAN and loopback IPs may take the default route;
 * any name or other address outside the tunnel could reach an impostor (CGNAT, local DNS search domains).
 */
fun needsTailnet(host: String): Boolean {
  if (!IPV4.matches(host) && ':' !in host) return true
  val address = InetAddress.getByName(host)
  return !((address is Inet4Address && address.isSiteLocalAddress) || address.isLoopbackAddress)
}

/**
 * Tracks the VPN holding a tailnet address. Apps get only the VPNs that apply to them,
 * and their link properties (addresses included) are not redacted for non-owners.
 * All updates run on the main thread.
 */
class Tailscale(private val context: Context) {
  private val vpns = mutableMapOf<Network, List<InetAddress>>()
  private val state = MutableStateFlow(false)
  val connected: StateFlow<Boolean> = state

  /** The tailnet network, or null while Tailscale is off. */
  @Volatile var network: Network? = null
    private set

  init {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    // Seeded synchronously so a cold start doesn't flash "Tailscale is off" before the first callback.
    cm.activeNetwork?.let { active ->
      val vpn = cm.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
      cm.getLinkProperties(active)?.takeIf { vpn }?.let { update(active, it) }
    }
    val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
    val callback =
      object : ConnectivityManager.NetworkCallback() {
        // Always follows onAvailable, so it also reports new networks.
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = update(network, linkProperties)

        override fun onLost(network: Network) {
          vpns.remove(network)
          refresh()
        }
      }
    cm.registerNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
  }

  private fun update(network: Network, linkProperties: LinkProperties) {
    vpns[network] = linkProperties.linkAddresses.map { it.address }
    refresh()
  }

  /** Main thread only. The callbacks keep the state current; calling this just republishes it. */
  fun refresh() {
    network = tailnet(vpns)
    state.value = network != null
  }

  /** Opens Tailscale, or its store page when it isn't installed. */
  fun openApp() {
    val intent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE) ?: Intent(Intent.ACTION_VIEW, PLAY_STORE_URL.toUri())
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}
