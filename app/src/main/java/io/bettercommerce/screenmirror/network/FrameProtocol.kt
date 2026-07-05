package io.bettercommerce.screenmirror.network

import java.io.DataOutputStream

/**
 * Minimal length-prefixed wire protocol for streaming H.264 over TCP.
 *
 * Stream = one FORMAT message (carries width/height + SPS/PPS) followed by many
 * FRAME messages. The Sender may also interleave FOCUS messages at any point to
 * make the Receiver highlight a spot ("presenter pointer"). Every message begins
 * with a 4-byte tag.
 *
 *   FORMAT: [tag=1][width][height][csd0Len][csd0][csd1Len][csd1]
 *   FRAME : [tag=2][ptsUs(8)][flags][size][data...]
 *   FOCUS : [tag=3][xNorm(float)][yNorm(float)]   // 0f..1f of the captured frame
 *
 * All integers/floats are big-endian (DataOutputStream/DataInputStream defaults).
 */
object FrameProtocol {
    /** Default port the Receiver listens on. */
    const val PORT = 7845

    const val TAG_FORMAT = 1
    const val TAG_FRAME = 2
    const val TAG_FOCUS = 3

    fun writeFormat(
        out: DataOutputStream,
        width: Int,
        height: Int,
        csd0: ByteArray,
        csd1: ByteArray,
    ) {
        out.writeInt(TAG_FORMAT)
        out.writeInt(width)
        out.writeInt(height)
        out.writeInt(csd0.size)
        out.write(csd0)
        out.writeInt(csd1.size)
        out.write(csd1)
        out.flush()
    }

    fun writeFrame(
        out: DataOutputStream,
        presentationTimeUs: Long,
        flags: Int,
        data: ByteArray,
        size: Int,
    ) {
        out.writeInt(TAG_FRAME)
        out.writeLong(presentationTimeUs)
        out.writeInt(flags)
        out.writeInt(size)
        out.write(data, 0, size)
    }

    /**
     * A "focus here" ping. [xNorm]/[yNorm] are fractions (0f..1f) of the captured
     * frame — top-left origin — so they map onto the Receiver's preview regardless
     * of either device's resolution.
     */
    fun writeFocus(out: DataOutputStream, xNorm: Float, yNorm: Float) {
        out.writeInt(TAG_FOCUS)
        out.writeFloat(xNorm)
        out.writeFloat(yNorm)
        out.flush()
    }
}
