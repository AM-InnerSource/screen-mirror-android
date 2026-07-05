package io.bettercommerce.screenmirror.network

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import io.bettercommerce.screenmirror.capture.EncodedFrameListener
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer

/**
 * [EncodedFrameListener] that streams encoded H.264 to a remote Receiver over TCP.
 *
 * [connect] must be called off the main thread before capture starts. After that,
 * the encoder's drain thread drives [onFormat] / [onFrame], which serialize via
 * [FrameProtocol] onto the socket.
 */
class NetworkSender(
    private val host: String,
    private val port: Int,
) : EncodedFrameListener {

    private var socket: Socket? = null
    private var out: DataOutputStream? = null

    /** Reusable staging buffer so we don't allocate per frame. */
    private var frameBuffer = ByteArray(0)

    /** Opens the TCP connection. Blocks — call from a background thread. */
    fun connect() {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        s.tcpNoDelay = true // latency over throughput
        socket = s
        out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
        Log.i(TAG, "Connected to $host:$port")
    }

    override fun onFormat(format: MediaFormat) {
        val stream = out ?: return
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val csd0 = format.getByteBuffer("csd-0")?.toByteArray() ?: ByteArray(0)
        val csd1 = format.getByteBuffer("csd-1")?.toByteArray() ?: ByteArray(0)
        try {
            synchronized(stream) {
                FrameProtocol.writeFormat(stream, width, height, csd0, csd1)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sending format failed", t)
        }
    }

    override fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val stream = out ?: return
        val size = info.size
        if (frameBuffer.size < size) frameBuffer = ByteArray(size)
        buffer.position(info.offset)
        buffer.get(frameBuffer, 0, size)
        try {
            synchronized(stream) {
                FrameProtocol.writeFrame(stream, info.presentationTimeUs, info.flags, frameBuffer, size)
                stream.flush()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sending frame failed", t)
        }
    }

    override fun onEnded() = close()

    fun close() {
        try {
            out?.flush()
        } catch (_: Throwable) {
        }
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        socket = null
        out = null
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val dup = duplicate()
        val bytes = ByteArray(dup.remaining())
        dup.get(bytes)
        return bytes
    }

    private companion object {
        const val TAG = "NetworkSender"
        const val CONNECT_TIMEOUT_MS = 5_000
    }
}
