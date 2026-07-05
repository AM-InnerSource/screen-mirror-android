package io.bettercommerce.screenmirror.ui.screens

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A video preview backed by a [TextureView].
 *
 * TextureView (rather than SurfaceView) is used deliberately: a SurfaceView lives
 * in its own window layer and, inside a Compose hierarchy, frequently shows up as
 * an opaque black "hole" due to z-ordering. TextureView composites like a normal
 * view, so the decoded frames actually appear.
 *
 * [onSurfaceAvailable] delivers a [Surface] to render into; [onSurfaceDestroyed]
 * signals it is gone so the caller can release its decoder.
 */
@Composable
fun VideoSurface(
    modifier: Modifier,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null

                    override fun onSurfaceTextureAvailable(
                        st: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        surface = Surface(st).also(onSurfaceAvailable)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        st: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        onSurfaceDestroyed()
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}
