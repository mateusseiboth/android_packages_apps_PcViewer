package org.lineageos.pcviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow
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
 * The image drawn with [transform]: drag to pan, pinch or mouse wheel to zoom around the pointer, double
 * click to switch between fit and actual size. The bitmap may be subsampled; it is drawn at [original] size.
 */
@Composable
fun ImagePane(bitmap: ImageBitmap, original: PixelSize, transform: ImageTransform?, gestures: ImageGestures) {
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
        drawTransformed(bitmap, original, transform)
    }
}

private fun Offset.fromCentre(size: IntSize) = Point2(x - size.width / 2f, y - size.height / 2f)

private fun DrawScope.drawTransformed(bitmap: ImageBitmap, original: PixelSize, transform: ImageTransform) {
    val centre = Offset(size.width / 2 + transform.offset.x, size.height / 2 + transform.offset.y)
    translate(centre.x, centre.y) {
        rotate(transform.rotation.toFloat(), pivot = Offset.Zero) {
            scale(transform.scale, pivot = Offset.Zero) {
                drawImage(
                    bitmap,
                    dstOffset = IntOffset(-original.width / 2, -original.height / 2),
                    dstSize = IntSize(original.width, original.height),
                    filterQuality = FilterQuality.High,
                )
            }
        }
    }
}
