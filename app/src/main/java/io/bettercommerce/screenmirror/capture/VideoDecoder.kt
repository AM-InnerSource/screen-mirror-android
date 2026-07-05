package io.bettercommerce.screenmirror.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Hardware H.264 decoder that renders decoded frames directly onto a [Surface].
 *
 * Configured from the encoder's output [MediaFormat] (which carries the SPS/PPS
 * in csd-0/csd-1), so no manual parameter-set handling is needed. Frames are fed
 * one at a time via [decode]; each call also drains and renders any ready output.
 */
class VideoDecoder(surface: Surface, format: MediaFormat) {

    private val codec: MediaCodec =
        MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, surface, null, 0)
            start()
        }

    private val bufferInfo = MediaCodec.BufferInfo()

    /**
     * Queues one encoded access unit for decoding and renders any decoded frames
     * that have become available. [data] must be positioned at [info].offset.
     */
    fun decode(data: ByteBuffer, info: MediaCodec.BufferInfo) {
        val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inIndex >= 0) {
            val input = codec.getInputBuffer(inIndex)
            if (input != null) {
                input.clear()
                data.position(info.offset)
                data.limit(info.offset + info.size)
                input.put(data)
                codec.queueInputBuffer(inIndex, 0, info.size, info.presentationTimeUs, 0)
            } else {
                codec.queueInputBuffer(inIndex, 0, 0, info.presentationTimeUs, 0)
            }
        }
        // else: no input buffer free this round — drop the frame (acceptable for M2).

        // Drain + render whatever is ready. releaseOutputBuffer(_, true) renders.
        var outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        while (outIndex >= 0) {
            codec.releaseOutputBuffer(outIndex, /* render = */ true)
            outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
        }
    }

    fun release() {
        try {
            codec.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "decoder stop failed", t)
        }
        try {
            codec.release()
        } catch (t: Throwable) {
            Log.w(TAG, "decoder release failed", t)
        }
    }

    private companion object {
        const val TAG = "VideoDecoder"
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
