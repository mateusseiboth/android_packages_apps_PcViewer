package org.lineageos.pcviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.launch
import org.lineageos.pcviewer.PdfDocument
import org.lineageos.pcviewer.PdfSearch
import org.lineageos.pcviewer.R
import org.lineageos.pcviewer.core.Box
import org.lineageos.pcviewer.core.LinkHits
import org.lineageos.pcviewer.core.LinkTarget
import org.lineageos.pcviewer.core.PageLink

private val PAGE_SPACING = 8.dp
private val MAX_PAGE_WIDTH = 960.dp
private const val WHEEL_ZOOM_STEP = 1.1f

data class PdfActions(
    val onZoomBy: (Float) -> Unit,
    val onPage: (Int) -> Unit,
    val onWebLink: (String) -> Unit,
)

/**
 * The pages one under the other, [zoom] times the width that fits the window; wider pages scroll sideways.
 * Ctrl + mouse wheel zooms, the wheel alone scrolls. Only the pages in view are drawn. Search matches are
 * highlighted and the selected one is scrolled into view; links open on click.
 */
@Composable
fun PdfPane(document: PdfDocument, zoom: Float, search: PdfSearch?, actions: PdfActions) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.collect(actions.onPage) }
    val selected = search?.cursor?.current
    LaunchedEffect(selected) { selected?.let { list.animateScrollToItem(it.page) } }
    val onLink: (PageLink) -> Unit = { link ->
        when (val target = link.target) {
            is LinkTarget.Web -> actions.onWebLink(target.uri)
            is LinkTarget.Page -> scope.launch { list.animateScrollToItem(target.index) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) { zoomWithCtrlWheel(actions.onZoomBy) }) {
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
                items(document.pageCount) { index ->
                    PdfPage(document, index, pageWidth, matchesOn(search, index), onLink)
                }
            }
        }
        PageIndicator(list, document.pageCount, Modifier.align(Alignment.BottomEnd))
    }
}

/** This page's matches: every match's boxes, and which of them is selected. */
private data class PageMatches(val all: List<List<Box>>, val selected: List<Box>?)

private fun matchesOn(search: PdfSearch?, page: Int): PageMatches {
    val cursor = search?.cursor ?: return PageMatches(emptyList(), null)
    val all = cursor.matches.filter { it.page == page }.map { it.match }
    return PageMatches(all, cursor.current?.takeIf { it.page == page }?.match)
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.zoomWithCtrlWheel(onZoomBy: (Float) -> Unit) {
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
}

@Composable
private fun PdfPage(document: PdfDocument, index: Int, width: Dp, matches: PageMatches, onLink: (PageLink) -> Unit) {
    val page = document.pageSizes[index]
    val targetWidth = with(LocalDensity.current) { width.toPx().roundToInt() }
    val bitmap by produceState<ImageBitmap?>(null, document, index, targetWidth) {
        value = document.render(index, targetWidth)?.asImageBitmap()
    }
    val links by produceState(emptyList<PageLink>(), document, index) { value = document.links(index) }
    val description = stringResource(R.string.page_indicator, index + 1, document.pageCount)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val current = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
    Box(
        Modifier.width(width).aspectRatio(page.width.toFloat() / page.height.coerceAtLeast(1))
            .shadow(2.dp).background(Color.White).semantics { contentDescription = description }
            .pointerInput(links, targetWidth) {
                detectTapGestures { at ->
                    val points = size.width.toFloat() / page.width.coerceAtLeast(1)
                    LinkHits.find(links, at.x / points, at.y / points)?.let(onLink)
                }
            },
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxWidth()) }
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.width / page.width.coerceAtLeast(1)
            matches.all.forEach { boxes ->
                val color = current.takeIf { boxes == matches.selected } ?: highlight
                boxes.forEach { box ->
                    drawRect(color, Offset(box.left * scale, box.top * scale), Size((box.right - box.left) * scale, (box.bottom - box.top) * scale))
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(list: LazyListState, count: Int, modifier: Modifier) {
    val page = list.firstVisibleItemIndex + 1
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
