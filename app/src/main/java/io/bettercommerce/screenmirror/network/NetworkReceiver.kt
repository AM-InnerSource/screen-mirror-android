package io.bettercommerce.screenmirror.network

import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import io.bettercommerce.screenmirror.capture.VideoDecoder
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException
import java.nio.ByteBuffer

/**
 * TCP server for the Receiver role: accepts one Sender connection, reads the
 * [FrameProtocol] stream, and renders decoded frames onto a [Surface].
 *
 * The accept/read loop runs on a background thread. Surface and decoder are
 * touched from both that thread and the UI thread, so access is guarded by
 * [lock]; the decoder is created lazily once both surface and format are known.
 */
class NetworkReceiver(
    private val port: Int,
    private val onStatus: (Status) -> Unit,
) {
    enum class Status { LISTENING, RECEIVING, STOPPED, ERROR }

    private val lock = Any()
    private var surface: Surface? = null
    private var pendingFormat: MediaFormat? = null
    private var decoder: VideoDecoder? = null

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null

    fun attachSurface(newSurface: Surface) = synchronized(lock) {
        surface = newSurface
        maybeCreateDecoder()
    }

    fun detachSurface() = synchronized(lock) {
        surface = null
        decoder?.release()
        decoder = null
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread(::serverLoop, "NetworkReceiver").also { it.start() }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close() // unblocks accept()
        } catch (_: Throwable) {
        }
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
        synchronized(lock) {
            decoder?.release()
            decoder = null
            pendingFormat = null
        }
    }

    private fun serverLoop() {
        try {
            val server = ServerSocket(port)
            serverSocket = server
            onStatus(Status.LISTENING)
            Log.i(TAG, "Listening on port $port")

            val client = server.accept()
            client.tcpNoDelay = true
            onStatus(Status.RECEIVING)
            Log.i(TAG, "Sender connected: ${client.inetAddress?.hostAddress}")

            DataInputStream(BufferedInputStream(client.getInputStream())).use { input ->
                readStream(input)
            }
            client.close()
        } catch (e: SocketException) {
            // Expected when stop() closes the server socket.
            if (running) {
                Log.e(TAG, "socket error", e)
                onStatus(Status.ERROR)
            }
        } catch (e: EOFException) {
            Log.i(TAG, "Sender disconnected")
        } catch (t: Throwable) {
            Log.e(TAG, "server loop failed", t)
            if (running) onStatus(Status.ERROR)
        } finally {
            if (running) onStatus(Status.STOPPED)
        }
    }

    private fun readStream(input: DataInputStream) {
        while (running) {
            when (input.readInt()) {
                FrameProtocol.TAG_FORMAT -> {
                    val width = input.readInt()
                    val height = input.readInt()
                    val csd0 = ByteArray(input.readInt()).also { input.readFully(it) }
                    val csd1 = ByteArray(input.readInt()).also { input.readFully(it) }

                    val format = MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC, width, height,
                    ).apply {
                        if (csd0.isNotEmpty()) setByteBuffer("csd-0", ByteBuffer.wrap(csd0))
                        if (csd1.isNotEmpty()) setByteBuffer("csd-1", ByteBuffer.wrap(csd1))
                    }
                    synchronized(lock) {
                        pendingFormat = format
                        maybeCreateDecoder()
                    }
                }

                FrameProtocol.TAG_FRAME -> {
                    val ptsUs = input.readLong()
                    val flags = input.readInt()
                    val size = input.readInt()
                    val data = ByteArray(size).also { input.readFully(it) }
                    synchronized(lock) {
                        decoder?.decode(data, size, ptsUs, flags)
                    }
                }

                else -> {
                    Log.w(TAG, "unknown tag; dropping connection")
                    return
                }
            }
        }
    }

    private fun maybeCreateDecoder() {
        val s = surface
        val f = pendingFormat
        if (decoder == null && s != null && f != null) {
            decoder = try {
                VideoDecoder(s, f)
            } catch (t: Throwable) {
                Log.e(TAG, "decoder create failed", t)
                onStatus(Status.ERROR)
                null
            }
        }
    }

    companion object {
        private const val TAG = "NetworkReceiver"
        private const val JOIN_TIMEOUT_MS = 1_500L

        /** Best-effort local WiFi/LAN IPv4 address to show the user. */
        fun localIpAddress(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                    ?.hostAddress
            } catch (t: Throwable) {
                null
            }
        }
    }
}
