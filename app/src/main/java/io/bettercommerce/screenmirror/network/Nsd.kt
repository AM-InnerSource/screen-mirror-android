package io.bettercommerce.screenmirror.network

/** Network Service Discovery (mDNS) constants for phone-to-phone pairing. */
object Nsd {
    /** Trailing dot is required by NsdManager. */
    const val SERVICE_TYPE = "_screenmirror._tcp."
}

/** A receiver found on the local network via [SenderDiscovery]. */
data class DiscoveredReceiver(
    val name: String,
    val host: String,
    val port: Int,
)
