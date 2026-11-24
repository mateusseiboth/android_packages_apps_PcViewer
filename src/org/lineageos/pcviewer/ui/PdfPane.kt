package org.lineageos.pcviewer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlin.math.roundToInt
import org.lineageos.pcviewer.PdfDocument
import org.lineageos.pcviewer.R

private val PAGE_SPACING = 8.dp
private val MAX_PAGE_WIDTH = 960.dp
private const val WHEEL_ZOOM_STEP = 1.1f

/**
 * The pages one under the other, [zoom] times the width that fits the window; wider pages scroll sideways.
 * Ctrl + mouse wheel zooms, the wheel alone scrolls. Only the pages in view are drawn.
 */
@Composable
fun PdfPane(document: PdfDocument, zoom: Float, onZoomBy: (Float) -> Unit) {
    val list = rememberLazyListState()
    val currentPage by remember { derivedStateOf { list.firstVisibleItemIndex + 1 } }
    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val isCtrlWheel = event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed
                    if (!isCtrlWheel) continue
                    val change = event.changes.first()
                    onZoomBy(WHEEL_ZOOM_STEP.pow(-change.scrollDelta.y))
                    change.consume()
                }
            }
        },
    ) {
        val fitWidth = minOf(maxWidth - PAGE_SPACING * 4, MAX_PAGE_WIDTH)
        val pageWidth = fitWidth * zoom
        val listWidth = maxOf(maxWidth, pageWidth + PAGE_SPACING * 4)
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                state = list,
                modifier = Modifier.width(listWidth).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = PAGE_SPACING * 2),
                verticalArrangement = Arrangement.spacedBy(PAGE_SPACING),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(document.pageCount) { index -> PdfPage(document, index, pageWidth) }
            }
        }
        PageIndicator(currentPage, document.pageCount, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun PdfPage(document: PdfDocument, index: Int, width: Dp) {
    val page = document.pageSizes[index]
    val targetWidth = with(LocalDensity.current) { width.toPx().roundToInt() }
    val bitmap by produceState<ImageBitmap?>(null, document, index, targetWidth) {
        value = document.render(index, targetWidth)?.asImageBitmap()
    }
    val description = stringResource(R.string.page_indicator, index + 1, document.pageCount)
    Box(
        Modifier.width(width).aspectRatio(page.width.toFloat() / page.height.coerceAtLeast(1))
            .shadow(2.dp).background(Color.White).semantics { contentDescription = description },
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun PageIndicator(page: Int, count: Int, modifier: Modifier) {
    Surface(
        modifier = modifier.padding(end = 42.dp, bottom = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Text(
            stringResource(R.string.page_indicator, page, count),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
        )
    }
}
