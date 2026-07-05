package io.bettercommerce.screenmirror.capture

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 * Sink for encoded H.264 output produced by [ScreenEncoder].
 *
 * Decoupling the encoder from what happens to its output is what lets the same
 * capture pipeline feed different destinations:
 *  - [MuxerFrameListener] — write an .mp4 file (M1)
 *  - [LoopbackController] — decode + render on this device (M2)
 *  - a network sender      — stream to another device (M3)
 *
 * Callbacks arrive on the encoder's drain thread. The [buffer] passed to
 * [onFrame] is only valid for the duration of the call — consume it synchronously.
 */
interface EncodedFrameListener {
    /**
     * Called once when the output format is known. The [format] carries the
     * codec-specific data (SPS/PPS in csd-0/csd-1) needed to configure a decoder
     * or muxer track.
     */
    fun onFormat(format: MediaFormat)

    /** A single encoded access unit. [buffer] is positioned at the frame data. */
    fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo)

    /** No more frames will arrive; flush/release. */
    fun onEnded()
}
