package com.hhuezo.pdfconverter.ui.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ZoomInMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import android.content.ClipData
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF as AndroidRectF
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.pdf.PdfDocumentSession
import com.hhuezo.pdfconverter.pdf.PdfHighlightRect
import com.hhuezo.pdfconverter.pdf.PdfPageTextLayer
import com.hhuezo.pdfconverter.pdf.PdfSearchMatch
import com.hhuezo.pdfconverter.pdf.PdfTextSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MinZoom = 1f
private const val MaxZoom = 5f

private data class PageTextSelection(
    val pageIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
) {
    val orderedStart: Int get() = min(startIndex, endIndex)
    val orderedEnd: Int get() = max(startIndex, endIndex)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    uri: Uri,
    displayName: String,
    initialPageIndex: Int = 0,
    onBack: () -> Unit,
    onPageChanged: (pageIndex: Int) -> Unit = {},
    onConvertToImage: () -> Unit = {},
    onSignPdf: () -> Unit = {},
    onDeletePages: () -> Unit = {},
    onRotatePages: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocusRequester = remember { FocusRequester() }
    val searcher = remember { PdfTextSearcher(context) }

    var session by remember { mutableStateOf<PdfDocumentSession?>(null) }
    var openError by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(MinZoom) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showGoToPage by remember { mutableStateOf(false) }
    var actionsBarVisible by remember { mutableStateOf(true) }
    var pageInput by remember { mutableStateOf("") }
    var pageInputError by remember { mutableStateOf(false) }
    var renderWidthPx by remember { mutableIntStateOf(0) }

    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<PdfSearchMatch>>(emptyList()) }
    var searchMatchIndex by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var hasActiveSearch by remember { mutableStateOf(false) }

    var textLayers by remember { mutableStateOf<Map<Int, PdfPageTextLayer>>(emptyMap()) }
    var textSelection by remember { mutableStateOf<PageTextSelection?>(null) }
    var isAdjustingSelection by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val latestOnPageChanged by rememberUpdatedState(onPageChanged)
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)
    val latestIsAdjustingSelection by rememberUpdatedState(isAdjustingSelection)

    val selectedText = remember(textSelection, textLayers) {
        val selection = textSelection ?: return@remember null
        val layer = textLayers[selection.pageIndex] ?: return@remember null
        layer.textInRange(selection.orderedStart, selection.orderedEnd)
            .takeIf { it.isNotBlank() }
    }

    fun updateTextSelection(pageIndex: Int, startIndex: Int, endIndex: Int) {
        textSelection = PageTextSelection(
            pageIndex = pageIndex,
            startIndex = min(startIndex, endIndex),
            endIndex = max(startIndex, endIndex),
        )
    }

    fun clearSearchResults() {
        searchMatches = emptyList()
        searchMatchIndex = 0
        searchMessage = null
        hasActiveSearch = false
        isSearching = false
    }

    fun clearTextSelection() {
        textSelection = null
        isAdjustingSelection = false
    }

    fun closeSearch() {
        searchVisible = false
        searchQuery = ""
        clearSearchResults()
        keyboardController?.hide()
    }

    fun copySelectedText() {
        val text = selectedText ?: return
        clearTextSelection()
        scope.launch {
            clipboard.setClipEntry(
                ClipEntry(ClipData.newPlainText("PDF", text)),
            )
            snackbarHostState.showSnackbar(context.getString(R.string.reader_copied))
        }
    }

    fun goToMatch(index: Int) {
        if (searchMatches.isEmpty()) return
        val size = searchMatches.size
        val safeIndex = ((index % size) + size) % size
        searchMatchIndex = safeIndex
        val page = searchMatches[safeIndex].pageIndex
        scope.launch {
            listState.animateScrollToItem(page)
        }
    }

    fun runSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty() || session == null) return
        keyboardController?.hide()
        scope.launch {
            isSearching = true
            searchMessage = null
            hasActiveSearch = false
            val result = withContext(Dispatchers.IO) {
                runCatching { searcher.search(uri, query) }
            }
            isSearching = false
            result.fold(
                onSuccess = { searchResult ->
                    hasActiveSearch = true
                    searchMatches = searchResult.matches
                    searchMatchIndex = 0
                    when {
                        !searchResult.hadExtractableText -> {
                            searchMessage = context.getString(R.string.reader_search_no_text)
                        }
                        searchResult.matches.isEmpty() -> {
                            searchMessage = context.getString(R.string.reader_search_no_results)
                        }
                        else -> {
                            searchMessage = null
                            goToMatch(0)
                        }
                    }
                },
                onFailure = {
                    searchMatches = emptyList()
                    searchMatchIndex = 0
                    hasActiveSearch = true
                    searchMessage = context.getString(R.string.reader_search_error)
                },
            )
        }
    }

    DisposableEffect(uri) {
        val opened = runCatching { PdfDocumentSession(context, uri) }
        session = opened.getOrNull()
        openError = opened.isFailure
        textLayers = emptyMap()
        textSelection = null
        onDispose {
            session?.close()
            session = null
            searcher.clearPageTextCache()
            textLayers = emptyMap()
            textSelection = null
        }
    }

    LaunchedEffect(searchVisible) {
        if (searchVisible) {
            delay(80)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    val currentPage by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex
        }
    }

    LaunchedEffect(uri, currentPage, session) {
        val doc = session ?: return@LaunchedEffect
        val pages = listOf(currentPage - 1, currentPage, currentPage + 1)
            .filter { it in 0 until doc.pageCount }
        val loaded = withContext(Dispatchers.IO) {
            pages.mapNotNull { page ->
                runCatching { page to searcher.loadPageTextLayer(uri, page) }.getOrNull()
            }
        }
        if (loaded.isNotEmpty()) {
            textLayers = textLayers + loaded
        }
    }

    LaunchedEffect(session, initialPageIndex) {
        val doc = session ?: return@LaunchedEffect
        val target = initialPageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        if (target > 0) {
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(listState, session) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .maxByOrNull { it.size }?.index
                ?: listState.firstVisibleItemIndex
        }
            .distinctUntilChanged()
            .collect { page ->
                latestOnPageChanged(page)
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            session?.let { doc ->
                                val zoomLabel = if (scale > 1.01f) {
                                    " · ${(scale * 100).roundToInt()}%"
                                } else {
                                    ""
                                }
                                Text(
                                    text = stringResource(
                                        R.string.reader_page_of,
                                        currentPage + 1,
                                        doc.pageCount,
                                    ) + zoomLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.reader_back),
                            )
                        }
                    },
                    actions = {
                        if (scale > 1.01f) {
                            IconButton(
                                onClick = {
                                    scale = MinZoom
                                    offset = Offset.Zero
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ZoomInMap,
                                    contentDescription = stringResource(R.string.reader_zoom_reset),
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (searchVisible) {
                                    closeSearch()
                                } else {
                                    searchVisible = true
                                }
                            },
                            enabled = session != null,
                        ) {
                            Icon(
                                imageVector = if (searchVisible) {
                                    Icons.Outlined.Close
                                } else {
                                    Icons.Outlined.Search
                                },
                                contentDescription = stringResource(
                                    if (searchVisible) {
                                        R.string.reader_search_close
                                    } else {
                                        R.string.reader_search
                                    },
                                ),
                            )
                        }
                        IconButton(
                            onClick = {
                                pageInput = (currentPage + 1).toString()
                                pageInputError = false
                                showGoToPage = true
                            },
                            enabled = session != null,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FindInPage,
                                contentDescription = stringResource(R.string.reader_go_to_page),
                            )
                        }
                        IconButton(
                            onClick = { actionsBarVisible = !actionsBarVisible },
                        ) {
                            Icon(
                                imageVector = if (actionsBarVisible) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
                                contentDescription = stringResource(
                                    if (actionsBarVisible) {
                                        R.string.reader_actions_hide
                                    } else {
                                        R.string.reader_actions_show
                                    },
                                ),
                            )
                        }
                    },
                    colors = androsTopAppBarColors(),
                )

                AnimatedVisibility(visible = actionsBarVisible) {
                    ReaderActionsBar(
                        enabled = session != null,
                        onSignPdf = onSignPdf,
                        onConvertToImage = onConvertToImage,
                        onDeletePages = onDeletePages,
                        onRotatePages = onRotatePages,
                    )
                }

                AnimatedVisibility(visible = searchVisible) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            if (hasActiveSearch) clearSearchResults()
                        },
                        onSearch = ::runSearch,
                        isSearching = isSearching,
                        enabled = session != null && !isSearching,
                        focusRequester = searchFocusRequester,
                    )
                }
            }
        },
        bottomBar = {
            when {
                selectedText != null -> {
                    TextSelectionBar(
                        preview = selectedText!!,
                        onCopy = ::copySelectedText,
                        onClear = ::clearTextSelection,
                    )
                }
                searchVisible && (hasActiveSearch || isSearching) -> {
                    SearchResultsBar(
                        isSearching = isSearching,
                        matches = searchMatches,
                        matchIndex = searchMatchIndex,
                        message = searchMessage,
                        onPrevious = { goToMatch(searchMatchIndex - 1) },
                        onNext = { goToMatch(searchMatchIndex + 1) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            openError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.reader_error_open),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            session == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.reader_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                val doc = session!!
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    val viewportWidthPx = with(density) { maxWidth.toPx() }
                    val viewportHeightPx = with(density) { maxHeight.toPx() }
                    val baseWidthPx = viewportWidthPx.roundToInt().coerceAtLeast(1)

                    LaunchedEffect(baseWidthPx) {
                        if (renderWidthPx == 0) {
                            renderWidthPx = baseWidthPx
                        }
                    }

                    LaunchedEffect(scale, baseWidthPx) {
                        delay(180)
                        renderWidthPx = (baseWidthPx * scale)
                            .roundToInt()
                            .coerceIn(baseWidthPx, (baseWidthPx * MaxZoom).roundToInt())
                    }

                    val isZoomed = scale > 1.01f

                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !isZoomed && !isAdjustingSelection,
                        modifier = Modifier
                            .fillMaxSize()
                            // Pellizco con dos dedos (prioridad alta).
                            .pointerInput(viewportWidthPx, viewportHeightPx) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.size >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val newScale =
                                                (latestScale * zoomChange)
                                                    .coerceIn(MinZoom, MaxZoom)

                                            val maxX =
                                                (viewportWidthPx * (newScale - 1f)) / 2f
                                            val maxY =
                                                (viewportHeightPx * (newScale - 1f)) / 2f
                                            val newOffset = if (newScale > 1.01f) {
                                                Offset(
                                                    x = (latestOffset.x + panChange.x)
                                                        .coerceIn(-maxX, maxX),
                                                    y = (latestOffset.y + panChange.y)
                                                        .coerceIn(-maxY, maxY),
                                                )
                                            } else {
                                                Offset.Zero
                                            }

                                            scale = newScale
                                            offset = newOffset
                                            pressed.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            // Pan con un dedo: se desactiva mientras se arrastra un asa de selección.
                            .pointerInput(viewportWidthPx, viewportHeightPx) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val pressed = event.changes.filter { it.pressed }
                                        if (
                                            pressed.size == 1 &&
                                            latestScale > 1.01f &&
                                            !latestIsAdjustingSelection &&
                                            pressed.none { it.isConsumed }
                                        ) {
                                            val panChange = event.calculatePan()
                                            val maxX =
                                                (viewportWidthPx * (latestScale - 1f)) / 2f
                                            val maxY =
                                                (viewportHeightPx * (latestScale - 1f)) / 2f
                                            offset = Offset(
                                                x = (latestOffset.x + panChange.x)
                                                    .coerceIn(-maxX, maxX),
                                                y = (latestOffset.y + panChange.y)
                                                    .coerceIn(-maxY, maxY),
                                            )
                                            pressed.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(doc.pageCount, key = { it }) { pageIndex ->
                            val pageHighlights = remember(searchMatches, pageIndex) {
                                searchMatches.mapIndexedNotNull { index, match ->
                                    if (match.pageIndex == pageIndex) {
                                        index to match.rects
                                    } else {
                                        null
                                    }
                                }
                            }
                            val pageSelection = textSelection
                                ?.takeIf { it.pageIndex == pageIndex }
                            val selectionRects = remember(pageSelection, textLayers[pageIndex]) {
                                val selection = pageSelection ?: return@remember emptyList()
                                val layer = textLayers[pageIndex] ?: return@remember emptyList()
                                layer.rectsInRange(selection.orderedStart, selection.orderedEnd)
                            }
                            PdfPageItem(
                                session = doc,
                                pageIndex = pageIndex,
                                targetWidthPx = renderWidthPx.coerceAtLeast(baseWidthPx),
                                highlights = pageHighlights,
                                activeMatchIndex = searchMatchIndex,
                                textLayer = textLayers[pageIndex],
                                selection = pageSelection,
                                selectionRects = selectionRects,
                                onSelectionChanged = { start, end ->
                                    updateTextSelection(pageIndex, start, end)
                                },
                                onClearSelection = ::clearTextSelection,
                                onAdjustingSelectionChange = { adjusting ->
                                    isAdjustingSelection = adjusting
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = if (pageSelection != null) 20.dp else 0.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGoToPage && session != null) {
        val pageCount = session!!.pageCount
        GoToPageDialog(
            pageInput = pageInput,
            pageCount = pageCount,
            hasError = pageInputError,
            onPageInputChange = {
                pageInput = it.filter { ch -> ch.isDigit() }.take(5)
                pageInputError = false
            },
            onDismiss = { showGoToPage = false },
            onConfirm = {
                val page = pageInput.toIntOrNull()
                if (page == null || page !in 1..pageCount) {
                    pageInputError = true
                } else {
                    showGoToPage = false
                    scope.launch {
                        listState.animateScrollToItem(page - 1)
                    }
                }
            },
        )
    }

}

@Composable
private fun ReaderActionsBar(
    enabled: Boolean,
    onSignPdf: () -> Unit,
    onConvertToImage: () -> Unit,
    onDeletePages: () -> Unit,
    onRotatePages: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            ReaderActionItem(
                icon = Icons.Outlined.Draw,
                label = stringResource(R.string.reader_action_sign),
                contentDescription = stringResource(R.string.reader_sign),
                enabled = enabled,
                onClick = onSignPdf,
                modifier = Modifier.weight(1f),
            )
            ReaderActionItem(
                icon = Icons.Outlined.Image,
                label = stringResource(R.string.reader_action_image),
                contentDescription = stringResource(R.string.reader_convert_to_image),
                enabled = enabled,
                onClick = onConvertToImage,
                modifier = Modifier.weight(1f),
            )
            ReaderActionItem(
                icon = Icons.Outlined.Rotate90DegreesCw,
                label = stringResource(R.string.reader_action_rotate),
                contentDescription = stringResource(R.string.reader_rotate_pages),
                enabled = enabled,
                onClick = onRotatePages,
                modifier = Modifier.weight(1f),
            )
            ReaderActionItem(
                icon = Icons.Outlined.DeleteSweep,
                label = stringResource(R.string.reader_action_delete_pages),
                contentDescription = stringResource(R.string.reader_delete_pages),
                enabled = enabled,
                onClick = onDeletePages,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReaderActionItem(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val disabledAlpha = 0.38f
    val iconTint = if (enabled) tint else tint.copy(alpha = disabledAlpha)
    val textColor = if (enabled) labelColor else labelColor.copy(alpha = disabledAlpha)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                enabled = enabled,
                label = { Text(stringResource(R.string.reader_search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.reader_search_close),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { if (enabled && query.isNotBlank()) onSearch() },
                ),
            )
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SearchResultsBar(
    isSearching: Boolean,
    matches: List<PdfSearchMatch>,
    matchIndex: Int,
    message: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarInsetPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                isSearching -> {
                    Text(
                        text = stringResource(R.string.reader_search_searching),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                message != null -> {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }

                matches.isNotEmpty() -> {
                    val current = matches[matchIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onPrevious,
                            enabled = matches.size > 1,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.reader_search_prev),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.reader_search_match_of,
                                    matchIndex + 1,
                                    matches.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    R.string.reader_search_page,
                                    current.pageIndex + 1,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onNext,
                            enabled = matches.size > 1,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.reader_search_next),
                            )
                        }
                    }
                    Text(
                        text = current.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSelectionBar(
    preview: String,
    onCopy: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        modifier = Modifier.navigationBarInsetPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reader_copy))
            }
            Text(
                text = preview.replace('\n', ' '),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.reader_selection_clear),
                )
            }
        }
    }
}

@Composable
private fun PdfPageItem(
    session: PdfDocumentSession,
    pageIndex: Int,
    targetWidthPx: Int,
    highlights: List<Pair<Int, List<PdfHighlightRect>>>,
    activeMatchIndex: Int,
    textLayer: PdfPageTextLayer?,
    selection: PageTextSelection?,
    selectionRects: List<PdfHighlightRect>,
    onSelectionChanged: (startIndex: Int, endIndex: Int) -> Unit,
    onClearSelection: () -> Unit,
    onAdjustingSelectionChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    val aspectRatio = remember(pageIndex, session) {
        runCatching { session.pageAspectRatio(pageIndex) }.getOrDefault(0.707f)
    }
    val pageShape = RoundedCornerShape(4.dp)
    val latestLayer by rememberUpdatedState(textLayer)
    val latestSelection by rememberUpdatedState(selection)
    val latestOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
    val latestOnClearSelection by rememberUpdatedState(onClearSelection)
    val latestOnAdjustingSelectionChange by rememberUpdatedState(onAdjustingSelectionChange)
    // Anchor word preserved while expanding with the initial long-press drag.
    var dragWordStart by remember(pageIndex) { mutableIntStateOf(-1) }
    var dragWordEnd by remember(pageIndex) { mutableIntStateOf(-1) }
    var draggingStartHandle by remember(pageIndex) { mutableStateOf(false) }
    var draggingEndHandle by remember(pageIndex) { mutableStateOf(false) }
    var handleDragX by remember(pageIndex) { mutableFloatStateOf(0f) }
    var handleDragY by remember(pageIndex) { mutableFloatStateOf(0f) }
    var fixedOppositeIndex by remember(pageIndex) { mutableIntStateOf(-1) }

    LaunchedEffect(session, pageIndex, targetWidthPx) {
        if (targetWidthPx <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            runCatching { session.renderPage(pageIndex, targetWidthPx) }.getOrNull()
        }
    }

    LaunchedEffect(selection) {
        if (selection == null) {
            draggingStartHandle = false
            draggingEndHandle = false
            fixedOppositeIndex = -1
            latestOnAdjustingSelectionChange(false)
        }
    }

    // Outer box is NOT clipped so selection handles remain visible and tappable.
    BoxWithConstraints(
        modifier = modifier.aspectRatio(aspectRatio),
    ) {
        val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val pageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = pageShape,
            shadowElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(pageShape)
                    .pointerInput(pageIndex) {
                        detectTapGestures(
                            onTap = { latestOnClearSelection() },
                        )
                    }
                    .pointerInput(pageIndex) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val layer = latestLayer
                                if (layer == null || layer.isEmpty()) {
                                    return@detectDragGesturesAfterLongPress
                                }
                                val index = layer.glyphIndexAt(
                                    x = (offset.x / size.width).coerceIn(0f, 1f),
                                    y = (offset.y / size.height).coerceIn(0f, 1f),
                                )
                                if (index == null) {
                                    dragWordStart = -1
                                    dragWordEnd = -1
                                    return@detectDragGesturesAfterLongPress
                                }
                                val word = layer.wordRangeAt(index)
                                dragWordStart = word.first
                                dragWordEnd = word.last
                                latestOnAdjustingSelectionChange(true)
                                latestOnSelectionChanged(word.first, word.last)
                            },
                            onDrag = { change, _ ->
                                val layer = latestLayer ?: return@detectDragGesturesAfterLongPress
                                if (dragWordStart < 0 || dragWordEnd < 0) {
                                    return@detectDragGesturesAfterLongPress
                                }
                                change.consume()
                                val index = layer.glyphIndexForHandle(
                                    x = (change.position.x / size.width).coerceIn(0f, 1f),
                                    y = (change.position.y / size.height).coerceIn(0f, 1f),
                                ) ?: return@detectDragGesturesAfterLongPress
                                val start = min(dragWordStart, index)
                                val end = max(dragWordEnd, index)
                                latestOnSelectionChanged(start, end)
                            },
                            onDragEnd = {
                                dragWordStart = -1
                                dragWordEnd = -1
                                latestOnAdjustingSelectionChange(false)
                            },
                            onDragCancel = {
                                dragWordStart = -1
                                dragWordEnd = -1
                                latestOnAdjustingSelectionChange(false)
                            },
                        )
                    },
            ) {
                val current = bitmap
                if (current != null && !current.isRecycled) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (highlights.isNotEmpty() || selectionRects.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val paint = AndroidPaint().apply {
                                isAntiAlias = true
                                style = AndroidPaint.Style.FILL
                            }
                            highlights.forEach { (matchIndex, rects) ->
                                paint.color = if (matchIndex == activeMatchIndex) {
                                    0x66FF1744.toInt()
                                } else {
                                    0x40FF5252.toInt()
                                }
                                rects.forEach { rect ->
                                    drawContext.canvas.nativeCanvas.drawRect(
                                        AndroidRectF(
                                            rect.left * size.width,
                                            rect.top * size.height,
                                            (rect.left + rect.width) * size.width,
                                            (rect.top + rect.height) * size.height,
                                        ),
                                        paint,
                                    )
                                }
                            }
                            if (selectionRects.isNotEmpty()) {
                                paint.color = 0x662196F3.toInt()
                                selectionRects.forEach { rect ->
                                    drawContext.canvas.nativeCanvas.drawRect(
                                        AndroidRectF(
                                            rect.left * size.width,
                                            rect.top * size.height,
                                            (rect.left + rect.width) * size.width,
                                            (rect.top + rect.height) * size.height,
                                        ),
                                        paint,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        val activeSelection = selection
        val layer = textLayer
        if (
            activeSelection != null &&
            layer != null &&
            !layer.isEmpty() &&
            activeSelection.orderedStart in layer.glyphs.indices &&
            activeSelection.orderedEnd in layer.glyphs.indices
        ) {
            val startGlyph = layer.glyphs[activeSelection.orderedStart]
            val endGlyph = layer.glyphs[activeSelection.orderedEnd]
            val handleColor = MaterialTheme.colorScheme.primary
            val startDesc = stringResource(R.string.reader_selection_handle_start)
            val endDesc = stringResource(R.string.reader_selection_handle_end)

            val startAnchorX =
                if (draggingStartHandle) handleDragX else startGlyph.left * pageWidthPx
            val startAnchorY =
                if (draggingStartHandle) handleDragY else startGlyph.bottom * pageHeightPx
            val endAnchorX =
                if (draggingEndHandle) handleDragX else endGlyph.right * pageWidthPx
            val endAnchorY =
                if (draggingEndHandle) handleDragY else endGlyph.bottom * pageHeightPx

            SelectionHandle(
                anchorXPx = startAnchorX,
                anchorYPx = startAnchorY,
                isStart = true,
                color = handleColor,
                active = draggingStartHandle,
                contentDescription = startDesc,
                onPressStart = {
                    val currentSelection = latestSelection ?: return@SelectionHandle
                    draggingStartHandle = true
                    draggingEndHandle = false
                    fixedOppositeIndex = currentSelection.orderedEnd
                    handleDragX = startGlyph.left * pageWidthPx
                    handleDragY = startGlyph.centerY * pageHeightPx
                    // Block page pan/scroll immediately on press.
                    latestOnAdjustingSelectionChange(true)
                },
                onDragBy = { dx, dy ->
                    handleDragX = (handleDragX + dx).coerceIn(0f, pageWidthPx)
                    handleDragY = (handleDragY + dy).coerceIn(0f, pageHeightPx)
                    val currentLayer = latestLayer ?: return@SelectionHandle
                    val index = currentLayer.glyphIndexForHandle(
                        x = handleDragX / pageWidthPx,
                        y = handleDragY / pageHeightPx,
                    ) ?: return@SelectionHandle
                    val opposite = fixedOppositeIndex
                    if (opposite >= 0) {
                        latestOnSelectionChanged(index, opposite)
                    }
                },
                onPressEnd = {
                    draggingStartHandle = false
                    fixedOppositeIndex = -1
                    latestOnAdjustingSelectionChange(false)
                },
            )
            SelectionHandle(
                anchorXPx = endAnchorX,
                anchorYPx = endAnchorY,
                isStart = false,
                color = handleColor,
                active = draggingEndHandle,
                contentDescription = endDesc,
                onPressStart = {
                    val currentSelection = latestSelection ?: return@SelectionHandle
                    draggingEndHandle = true
                    draggingStartHandle = false
                    fixedOppositeIndex = currentSelection.orderedStart
                    handleDragX = endGlyph.right * pageWidthPx
                    handleDragY = endGlyph.centerY * pageHeightPx
                    latestOnAdjustingSelectionChange(true)
                },
                onDragBy = { dx, dy ->
                    handleDragX = (handleDragX + dx).coerceIn(0f, pageWidthPx)
                    handleDragY = (handleDragY + dy).coerceIn(0f, pageHeightPx)
                    val currentLayer = latestLayer ?: return@SelectionHandle
                    val index = currentLayer.glyphIndexForHandle(
                        x = handleDragX / pageWidthPx,
                        y = handleDragY / pageHeightPx,
                    ) ?: return@SelectionHandle
                    val opposite = fixedOppositeIndex
                    if (opposite >= 0) {
                        latestOnSelectionChanged(opposite, index)
                    }
                },
                onPressEnd = {
                    draggingEndHandle = false
                    fixedOppositeIndex = -1
                    latestOnAdjustingSelectionChange(false)
                },
            )
        }
    }
}

@Composable
private fun SelectionHandle(
    anchorXPx: Float,
    anchorYPx: Float,
    isStart: Boolean,
    color: Color,
    active: Boolean,
    contentDescription: String,
    onPressStart: () -> Unit,
    onDragBy: (dx: Float, dy: Float) -> Unit,
    onPressEnd: () -> Unit,
) {
    val density = LocalDensity.current
    // Larger box while active so the soft halo isn't clipped under the finger.
    val hitWidth = if (active) 52.dp else 32.dp
    val hitHeight = if (active) 56.dp else 36.dp
    val hitWidthPx = with(density) { hitWidth.toPx() }
    val knobRadius = with(density) { if (active) 7.dp.toPx() else 5.5.dp.toPx() }
    val sideNudgePx = with(density) { 6.dp.toPx() }
    val latestOnPressStart by rememberUpdatedState(onPressStart)
    val latestOnDragBy by rememberUpdatedState(onDragBy)
    val latestOnPressEnd by rememberUpdatedState(onPressEnd)
    val activeColor = if (active) {
        color
    } else {
        color.copy(alpha = 0.92f)
    }

    Box(
        modifier = Modifier
            .zIndex(if (isStart) 2f else 3f)
            .wrapContentSize(unbounded = true)
            .offset {
                val nudgedX = if (isStart) {
                    anchorXPx - sideNudgePx
                } else {
                    anchorXPx + sideNudgePx
                }
                IntOffset(
                    x = (nudgedX - hitWidthPx / 2f).roundToInt(),
                    y = (anchorYPx - 2f).roundToInt(),
                )
            }
            .size(width = hitWidth, height = hitHeight)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(isStart) {
                // Capture press immediately so page pan/scroll cannot steal the gesture.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    latestOnPressStart()
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break
                            if (change.changedToUp() || !change.pressed) {
                                change.consume()
                                break
                            }
                            val drag = change.positionChange()
                            change.consume()
                            if (drag.x != 0f || drag.y != 0f) {
                                latestOnDragBy(drag.x, drag.y)
                            }
                        }
                    } finally {
                        latestOnPressEnd()
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val caretTop = 1f
            val caretHeight = knobRadius * 1.1f
            val knobCenterY = caretTop + caretHeight + knobRadius * 0.15f
            if (active) {
                // Soft halo while the handle is held.
                drawCircle(
                    color = color.copy(alpha = 0.28f),
                    radius = knobRadius * 3.6f,
                    center = Offset(cx, knobCenterY),
                )
            }
            drawRect(
                color = activeColor,
                topLeft = Offset(cx - 1.25f, caretTop),
                size = Size(2.5f, caretHeight),
            )
            drawCircle(
                color = activeColor,
                radius = knobRadius,
                center = Offset(cx, knobCenterY),
            )
        }
    }
}

@Composable
private fun GoToPageDialog(
    pageInput: String,
    pageCount: Int,
    hasError: Boolean,
    onPageInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = onPageInputChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.reader_go_to_page_hint)) },
                    isError = hasError,
                    supportingText = if (hasError) {
                        {
                            Text(stringResource(R.string.reader_error_invalid_page, pageCount))
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { onConfirm() }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.reader_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_cancel))
            }
        },
    )
}
