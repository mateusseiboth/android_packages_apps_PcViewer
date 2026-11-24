package org.lineageos.pcviewer.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow
import org.lineageos.pcviewer.Picture
import org.lineageos.pcviewer.core.ImageTransform
import org.lineageos.pcviewer.core.PixelSize
import org.lineageos.pcviewer.core.Point2
import org.lineageos.pcviewer.core.Size2

private const val WHEEL_ZOOM_STEP = 1.1f

data class ImageGestures(
    val onViewport: (Size2) -> Unit,
    val onTransform: ((ImageTransform) -> ImageTransform) -> Unit,
)

/**
 * The picture drawn with [transform]: drag to pan, pinch or mouse wheel to zoom around the pointer, double
 * click to switch between fit and actual size. Bitmaps may be subsampled and animations have their own
 * size; both are drawn at [original] size. An animation runs while it is on screen.
 */
@Composable
fun PicturePane(picture: Picture, original: PixelSize, transform: ImageTransform?, gestures: ImageGestures) {
    val frame = animationFrames(picture)
    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { gestures.onViewport(Size2(it.width.toFloat(), it.height.toFloat())) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val focus = centroid.fromCentre(size)
                    gestures.onTransform { it.zoomBy(zoom, focus).panBy(pan.x, pan.y) }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { position ->
                    gestures.onTransform { it.toggleZoom(position.fromCentre(size)) }
                })
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.first()
                        val factor = WHEEL_ZOOM_STEP.pow(-change.scrollDelta.y)
                        gestures.onTransform { it.zoomBy(factor, change.position.fromCentre(size)) }
                        change.consume()
                    }
                }
            },
    ) {
        transform ?: return@Canvas
        frame.value
        transformed(original, transform) { drawPicture(picture, original) }
    }
}

/** Counts the animation's frames so the canvas redraws on each; starts it while shown. */
@Composable
private fun animationFrames(picture: Picture): androidx.compose.runtime.MutableIntState {
    val frame = remember(picture) { mutableIntStateOf(0) }
    DisposableEffect(picture) {
        val moving = (picture as? Picture.Moving)?.drawable
        moving?.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) {
                frame.intValue++
            }

            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit

            override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
        }
        moving?.start()
        onDispose {
            moving?.stop()
            moving?.callback = null
        }
    }
    return frame
}

private fun Offset.fromCentre(size: IntSize) = Point2(x - size.width / 2f, y - size.height / 2f)

private fun DrawScope.transformed(original: PixelSize, transform: ImageTransform, draw: DrawScope.() -> Unit) {
    val centre = Offset(size.width / 2 + transform.offset.x, size.height / 2 + transform.offset.y)
    translate(centre.x, centre.y) {
        rotate(transform.rotation.toFloat(), pivot = Offset.Zero) {
            scale(transform.scale, pivot = Offset.Zero) {
                translate(-original.width / 2f, -original.height / 2f, draw)
            }
        }
    }
}

private fun DrawScope.drawPicture(picture: Picture, original: PixelSize) {
    when (picture) {
        is Picture.Still -> drawImage(
            picture.bitmap.asImageBitmap(),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(original.width, original.height),
            filterQuality = FilterQuality.High,
        )
        is Picture.Moving -> drawIntoCanvas { canvas ->
            picture.drawable.setBounds(0, 0, original.width, original.height)
            picture.drawable.draw(canvas.nativeCanvas)
        }
    }
}
