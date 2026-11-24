package org.lineageos.pcviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.RotateLeft
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.ViewerViewModel
import org.lineageos.pcviewer.core.Affine
import org.lineageos.pcviewer.core.CropAspect
import org.lineageos.pcviewer.core.CropBox
import org.lineageos.pcviewer.core.CropHandle
import org.lineageos.pcviewer.core.ImageEdit
import org.lineageos.pcviewer.core.PixelSize

private val ASPECT_LABELS = mapOf(
    CropAspect.FREE to R.string.aspect_free,
    CropAspect.ORIGINAL to R.string.aspect_original,
    CropAspect.SQUARE to R.string.aspect_square,
    CropAspect.WIDE to R.string.aspect_wide,
    CropAspect.STANDARD to R.string.aspect_standard,
    CropAspect.PHOTO to R.string.aspect_photo,
)

private val PREVIEW_MARGIN = 32.dp
private val HANDLE_TOUCH = 24.dp

/** The image as it will be saved, with the crop box to drag, and the tools on the right. */
@Composable
fun EditPane(bitmap: android.graphics.Bitmap, original: PixelSize, vm: ViewerViewModel) {
    val edit = vm.edit ?: return
    Row(Modifier.fillMaxSize()) {
        EditPreview(bitmap, original, edit, vm, Modifier.weight(1f))
        EditTools(edit, vm)
    }
}

@Composable
private fun EditPreview(
    bitmap: android.graphics.Bitmap,
    original: PixelSize,
    edit: ImageEdit,
    vm: ViewerViewModel,
    modifier: Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val current by rememberUpdatedState(edit)
    var handle by remember { mutableStateOf<CropHandle?>(null) }
    BoxWithConstraints(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer)) {
        val margin = with(androidx.compose.ui.platform.LocalDensity.current) { PREVIEW_MARGIN.toPx() }
        val touch = with(androidx.compose.ui.platform.LocalDensity.current) { HANDLE_TOUCH.toPx() }
        val area = Size(constraints.maxWidth - 2 * margin, constraints.maxHeight - 2 * margin)
        val placed = placement(edit.orientedSize(original.width, original.height), area, margin)
        Canvas(
            Modifier.fillMaxSize().pointerInput(placed) {
                detectDragGestures(
                    onDragStart = { at ->
                        val crop = current.crop
                        handle = CropBox.handleAt(
                            crop,
                            (at.x - placed.left) / placed.width,
                            (at.y - placed.top) / placed.height,
                            touch / placed.width,
                            touch / placed.height,
                        )
                    },
                    onDragEnd = { handle = null },
                    onDrag = { change, amount ->
                        handle?.let { vm.dragCrop(it, amount.x / placed.width, amount.y / placed.height) }
                        change.consume()
                    },
                )
            },
        ) {
            drawEdited(image, original, edit, placed)
            drawCropBox(edit, placed)
        }
    }
}

/** Where the turned image sits: fitted in [area] and centred. */
private fun placement(oriented: PixelSize, area: Size, margin: Float): Rect {
    val scale = min(area.width / oriented.width, area.height / oriented.height)
    val w = oriented.width * scale
    val h = oriented.height * scale
    val left = margin + (area.width - w) / 2
    val top = margin + (area.height - h) / 2
    return Rect(left, top, left + w, top + h)
}

private fun DrawScope.drawEdited(image: androidx.compose.ui.graphics.ImageBitmap, original: PixelSize, edit: ImageEdit, placed: Rect) {
    val oriented = edit.orientedSize(original.width, original.height)
    val scale = placed.width / oriented.width
    withTransform({
        translate(placed.left, placed.top)
        scale(scale, scale, pivot = Offset.Zero)
        transform(composeMatrix(edit.geometry(original.width, original.height)))
    }) {
        drawImage(
            image,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(original.width, original.height),
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(edit.colorMatrix())),
        )
    }
}

/** Dims what will be cut away and draws the box with its corner handles. */
private fun DrawScope.drawCropBox(edit: ImageEdit, placed: Rect) {
    val crop = Rect(
        placed.left + edit.crop.x * placed.width,
        placed.top + edit.crop.y * placed.height,
        placed.left + edit.crop.right * placed.width,
        placed.top + edit.crop.bottom * placed.height,
    )
    val shade = Color.Black.copy(alpha = 0.55f)
    drawRect(shade, Offset(placed.left, placed.top), Size(placed.width, crop.top - placed.top))
    drawRect(shade, Offset(placed.left, crop.bottom), Size(placed.width, placed.bottom - crop.bottom))
    drawRect(shade, Offset(placed.left, crop.top), Size(crop.left - placed.left, crop.height))
    drawRect(shade, Offset(crop.right, crop.top), Size(placed.right - crop.right, crop.height))
    drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(width = 2.dp.toPx()))
    val corner = 12.dp.toPx()
    listOf(crop.topLeft, crop.topRight, crop.bottomLeft, crop.bottomRight).forEach { point ->
        drawRect(Color.White, Offset(point.x - corner / 2, point.y - corner / 2), Size(corner, corner))
    }
}

private fun composeMatrix(affine: Affine) = Matrix().apply {
    values[Matrix.ScaleX] = affine.a
    values[Matrix.SkewY] = affine.b
    values[Matrix.SkewX] = affine.c
    values[Matrix.ScaleY] = affine.d
    values[Matrix.TranslateX] = affine.e
    values[Matrix.TranslateY] = affine.f
}

@Composable
private fun EditTools(edit: ImageEdit, vm: ViewerViewModel) {
    Column(
        Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(R.string.crop)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CropAspect.entries.forEach { aspect ->
                FilterChip(
                    selected = vm.cropAspect == aspect,
                    onClick = { vm.chooseCropAspect(aspect) },
                    label = { Text(stringResource(ASPECT_LABELS.getValue(aspect))) },
                )
            }
        }
        Section(R.string.rotate_flip)
        Row {
            Tool(R.string.rotate_left, { Icon(Icons.AutoMirrored.Outlined.RotateLeft, it) }) { vm.rotateLeft() }
            Tool(R.string.rotate_right, { Icon(Icons.AutoMirrored.Outlined.RotateRight, it) }) { vm.rotateRight() }
            Tool(R.string.flip_horizontal, { Icon(Icons.Outlined.Flip, it) }) { vm.updateEdit { e -> e.flipHorizontally() } }
            Tool(R.string.flip_vertical, { Icon(Icons.Outlined.Flip, it, Modifier.rotate(90f)) }) {
                vm.updateEdit { e -> e.flipVertically() }
            }
        }
        Section(R.string.adjust)
        Text(stringResource(R.string.brightness), style = MaterialTheme.typography.bodyMedium)
        Slider(value = edit.brightness, onValueChange = { v -> vm.updateEdit { it.copy(brightness = v) } }, valueRange = -1f..1f)
        Text(stringResource(R.string.contrast), style = MaterialTheme.typography.bodyMedium)
        Slider(value = edit.contrast, onValueChange = { v -> vm.updateEdit { it.copy(contrast = v) } }, valueRange = 0f..2f)
        Box(Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = { vm.updateEdit { it.reset() } }, enabled = !edit.isIdentity) {
                Text(stringResource(R.string.reset))
            }
        }
    }
}

@Composable
private fun Section(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Tool(label: Int, icon: @Composable (String) -> Unit, onClick: () -> Unit) {
    val description = stringResource(label)
    IconButton(onClick = onClick) { icon(description) }
}
