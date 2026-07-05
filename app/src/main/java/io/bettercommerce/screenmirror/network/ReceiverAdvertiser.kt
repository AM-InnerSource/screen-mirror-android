package io.bettercommerce.screenmirror.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Advertises the Receiver on the local network via NSD/mDNS so a Sender can
 * discover it without the user typing an IP address.
 */
class ReceiverAdvertiser(context: Context) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun register(port: Int, serviceName: String) {
        unregister()
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = Nsd.SERVICE_TYPE
            this.port = port
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "registered as ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = l
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (t: Throwable) {
            Log.w(TAG, "registerService threw", t)
        }
    }

    fun unregister() {
        listener?.let {
            try {
                nsd.unregisterService(it)
            } catch (_: Throwable) {
            }
        }
        listener = null
    }

    private companion object {
        const val TAG = "ReceiverAdvertiser"
    }
}
