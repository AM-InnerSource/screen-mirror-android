package io.bettercommerce.screenmirror.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Browses the local network for advertised receivers and resolves each to a
 * host:port, exposed as an observable [receivers] list for the Sender UI.
 *
 * NsdManager can only resolve one service at a time, so resolves are serialised
 * through a small queue.
 */
class SenderDiscovery(context: Context) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _receivers = MutableStateFlow<List<DiscoveredReceiver>>(emptyList())
    val receivers: StateFlow<List<DiscoveredReceiver>> = _receivers.asStateFlow()

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        stop()
        _receivers.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                enqueueResolve(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _receivers.update { list -> list.filterNot { it.name == info.serviceName } }
            }
        }
        discoveryListener = l
        try {
            nsd.discoverServices(Nsd.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (t: Throwable) {
            Log.w(TAG, "discoverServices threw", t)
        }
    }

    fun stop() {
        discoveryListener?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Throwable) {
            }
        }
        discoveryListener = null
        synchronized(resolveQueue) {
            resolveQueue.clear()
            resolving = false
        }
    }

    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) { resolveQueue.addLast(info) }
        pump()
    }

    // resolveService + NsdServiceInfo.host: the non-deprecated replacements only
    // exist on API 34+, so the classic API is used to support min SDK 24.
    @Suppress("DEPRECATION")
    private fun pump() {
        val next = synchronized(resolveQueue) {
            if (resolving) return
            val n = resolveQueue.removeFirstOrNull() ?: return
            resolving = true
            n
        }
        try {
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "resolve failed: $errorCode")
                    finishResolve()
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val host = info.host?.hostAddress
                    if (host != null) {
                        val receiver = DiscoveredReceiver(info.serviceName, host, info.port)
                        _receivers.update { list ->
                            list.filterNot { it.name == receiver.name } + receiver
                        }
                    }
                    finishResolve()
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "resolveService threw", t)
            finishResolve()
        }
    }

    private fun finishResolve() {
        synchronized(resolveQueue) { resolving = false }
        pump()
    }

    private companion object {
        const val TAG = "SenderDiscovery"
    }
}
