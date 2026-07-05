package io.bettercommerce.screenmirror.capture

/**
 * Encoder quality parameters. Chosen per subscription tier so Pro gets a
 * genuinely better stream (higher resolution / framerate / bitrate).
 */
data class CaptureConfig(
    /** Cap on the longer screen edge, in pixels (controls resolution). */
    val maxLongEdge: Int,
    val frameRate: Int,
    val bitRate: Int,
    val keyFrameIntervalSec: Int = 1,
) {
    companion object {
        val FREE = CaptureConfig(maxLongEdge = 1280, frameRate = 30, bitRate = 4_000_000)
        val PRO = CaptureConfig(maxLongEdge = 1920, frameRate = 60, bitRate = 10_000_000)

        fun forTier(isPro: Boolean): CaptureConfig = if (isPro) PRO else FREE
    }
}
