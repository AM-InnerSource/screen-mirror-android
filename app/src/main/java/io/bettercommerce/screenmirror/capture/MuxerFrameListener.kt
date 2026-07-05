package io.bettercommerce.screenmirror.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Writes encoded frames to an MP4 file (the M1 destination). Preserved as a
 * listener so the file path and the loopback path share one capture pipeline.
 */
class MuxerFrameListener(outputFile: File) : EncodedFrameListener {

    val path: String = outputFile.absolutePath

    private val muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var trackIndex = -1
    private var started = false

    override fun onFormat(format: MediaFormat) {
        trackIndex = muxer.addTrack(format)
        muxer.start()
        started = true
    }

    override fun onFrame(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (started && info.size > 0) {
            muxer.writeSampleData(trackIndex, buffer, info)
        }
    }

    override fun onEnded() {
        try {
            if (started) muxer.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "muxer stop failed", t)
        } finally {
            muxer.release()
        }
    }

    private companion object {
        const val TAG = "MuxerFrameListener"
    }
}
