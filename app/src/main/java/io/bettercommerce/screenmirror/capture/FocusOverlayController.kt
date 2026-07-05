package io.bettercommerce.screenmirror.capture

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.min

/**
 * A floating "presenter pointer" the Sender uses to highlight a spot for the
 * viewer while streaming.
 *
 * Because MediaProjection runs with the app in the background, there's no Compose
 * surface to tap — so this is a system overlay window (needs SYSTEM_ALERT_WINDOW).
 * The user **drags** the bubble over what they want to point at, then
 * **triple-taps** it (more than a double tap, so a stray double tap won't fire):
 * the bubble's centre — normalised against the real display size (which is exactly
 * what MediaProjection captures) — is reported via [onPing] and streamed to the
 * Receiver, which draws a matching ripple.
 *
 * All methods must be called on the main thread.
 */
class FocusOverlayController(
    private val context: Context,
    private val onPing: (xNorm: Float, yNorm: Float) -> Unit,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var bubble: FocusBubbleView? = null
    private var params: WindowManager.LayoutParams? = null

    private val displaySize: Point = realDisplaySize()

    /** Adds the bubble, or quietly no-ops if the overlay permission isn't granted. */
    fun show() {
        if (bubble != null) return
        if (!canDrawOverlay()) {
            Log.i(TAG, "overlay permission not granted; presenter pointer disabled")
            return
        }

        val sizePx = (BUBBLE_DP * context.resources.displayMetrics.density).toInt()
        val view = FocusBubbleView(context)
        val lp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displaySize.x - sizePx) / 2
            y = displaySize.y * 2 / 3
        }

        val slop = ViewConfiguration.get(context).scaledTouchSlop

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        // Fire only past a double tap: count quick in-place taps and ping on the 3rd.
        var tapCount = 0
        var lastTapUpTime = 0L
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceIn(0, displaySize.x - sizePx)
                        lp.y = (startY + dy).toInt().coerceIn(0, displaySize.y - sizePx)
                        runCatching { windowManager.updateViewLayout(view, lp) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        tapCount = 0 // a drag breaks the tap streak
                    } else {
                        tapCount = if (event.eventTime - lastTapUpTime <= MULTI_TAP_WINDOW_MS) {
                            tapCount + 1
                        } else {
                            1
                        }
                        lastTapUpTime = event.eventTime
                        if (tapCount >= REQUIRED_TAPS) {
                            tapCount = 0
                            ping()
                            view.pulse()
                        }
                    }
                }
            }
            true
        }

        runCatching { windowManager.addView(view, lp) }
            .onSuccess {
                bubble = view
                params = lp
            }
            .onFailure { Log.e(TAG, "failed to add overlay", it) }
    }

    fun hide() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        params = null
    }

    private fun ping() {
        val lp = params ?: return
        val size = lp.width
        val cx = (lp.x + size / 2f) / displaySize.x
        val cy = (lp.y + size / 2f) / displaySize.y
        onPing(cx.coerceIn(0f, 1f), cy.coerceIn(0f, 1f))
    }

    private fun canDrawOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun realDisplaySize(): Point {
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            point.set(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
        }
        return point
    }

    private companion object {
        const val TAG = "FocusOverlay"
        const val BUBBLE_DP = 64f

        /** Max gap between taps to count them as one multi-tap streak. */
        const val MULTI_TAP_WINDOW_MS = 450L

        /** Taps needed to ping — 3 means "more than a double tap". */
        const val REQUIRED_TAPS = 3
    }
}

/**
 * The bubble itself: a translucent target ring the user drags, plus a one-shot
 * outward "pulse" drawn locally when triple-tapped so the presenter gets the same
 * confirmation the viewer sees.
 */
private class FocusBubbleView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 79, 124, 255)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * context.resources.displayMetrics.density
        color = Color.argb(230, 79, 124, 255)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
        color = Color.argb(230, 79, 124, 255)
    }

    /** 0f = idle, 0f..1f = pulse progress. */
    private var pulseProgress = -1f
    private var animator: ValueAnimator? = null

    fun pulse() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550L
            addUpdateListener {
                pulseProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    pulseProgress = -1f
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(cx, cy) - ring.strokeWidth
        val inset = ring.strokeWidth * 1.5f

        canvas.drawCircle(cx, cy, baseRadius - inset, fill)
        canvas.drawCircle(cx, cy, baseRadius - inset, ring)
        canvas.drawCircle(cx, cy, ring.strokeWidth, dot)

        val p = pulseProgress
        if (p in 0f..1f) {
            pulsePaint.alpha = (230 * (1f - p)).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, (baseRadius - inset) + p * inset * 3f, pulsePaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
