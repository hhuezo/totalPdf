package com.hhuezo.pdfconverter.ui.sign

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.ZoomInMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.pdf.PdfDocumentSession
import com.hhuezo.pdfconverter.pdf.PdfSigner
import com.hhuezo.pdfconverter.pdf.PdfStampOverlay
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.ui.theme.Primary
import com.hhuezo.pdfconverter.ui.theme.PrimaryContainer
import com.hhuezo.pdfconverter.util.PdfFileSaver
import com.hhuezo.pdfconverter.util.queryPdfInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MinZoom = 1f
private const val MaxZoom = 5f

private enum class SignTool {
    Signature,
    Text,
    Initials,
    Date,
}

private enum class SignExportState {
    Editing,
    Processing,
    Ready,
}

private data class PageOverlay(
    val id: String,
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val bitmap: Bitmap,
)

private sealed class PendingPlacement {
    data class Stamp(val bitmap: Bitmap, val preferredWidthFrac: Float) : PendingPlacement()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSignScreen(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snackbar = remember { SnackbarHostState() }
    val fileInfo = remember(uri) { context.queryPdfInfo(uri) }
    var scale by remember { mutableFloatStateOf(MinZoom) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)

    var session by remember { mutableStateOf<PdfDocumentSession?>(null) }
    var openError by remember { mutableStateOf(false) }
    var overlays by remember { mutableStateOf<List<PageOverlay>>(emptyList()) }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var selectedTool by remember { mutableStateOf<SignTool?>(null) }
    var pendingPlacement by remember { mutableStateOf<PendingPlacement?>(null) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showInitialsPad by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    var exportState by remember { mutableStateOf(SignExportState.Editing) }
    var signedFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(uri) {
        val opened = runCatching { PdfDocumentSession(context, uri) }
        session = opened.getOrNull()
        openError = opened.isFailure
        onDispose {
            session?.close()
            session = null
            overlays.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        }
    }

    fun placeStamp(pageIndex: Int, left: Float, top: Float, stamp: PendingPlacement.Stamp) {
        val doc = session ?: return
        val aspect = runCatching { doc.pageAspectRatio(pageIndex) }.getOrDefault(0.707f)
        val width = stamp.preferredWidthFrac.coerceIn(0.12f, 0.55f)
        val height = (width / aspect) *
            (stamp.bitmap.height.toFloat() / stamp.bitmap.width.toFloat().coerceAtLeast(1f))
        val clampedLeft = left.coerceIn(0f, 1f - width)
        val clampedTop = top.coerceIn(0f, 1f - height.coerceAtMost(0.5f))
        val overlay = PageOverlay(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            left = clampedLeft,
            top = clampedTop,
            width = width,
            height = height.coerceAtMost(0.45f),
            bitmap = stamp.bitmap,
        )
        overlays = overlays + overlay
        selectedOverlayId = overlay.id
        pendingPlacement = null
        selectedTool = null
    }


    fun shareSignedPdf(file: File) {
        val shareUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                share,
                context.getString(R.string.sign_share_title),
            ),
        )
    }

    fun downloadSignedPdf(file: File) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val baseName = fileInfo.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
                PdfFileSaver.saveToDownloads(
                    context = context,
                    source = file,
                    displayName = "${baseName}_firmado.pdf",
                )
            }
            if (saved != null) {
                snackbar.showSnackbar(context.getString(R.string.sign_save_success))
            } else {
                snackbar.showSnackbar(context.getString(R.string.sign_download_error))
            }
        }
    }

    fun finalizeAndSave() {
        val currentOverlays = overlays
        if (currentOverlays.isEmpty()) {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.sign_empty_overlays))
            }
            return
        }
        scope.launch {
            exportState = SignExportState.Processing
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stampCopies = mutableListOf<Bitmap>()
                    val stamps = currentOverlays.map { overlay ->
                        val copy = overlay.bitmap.copy(
                            Bitmap.Config.ARGB_8888,
                            false,
                        ) ?: overlay.bitmap
                        if (copy !== overlay.bitmap) stampCopies += copy
                        PdfStampOverlay(
                            pageIndex = overlay.pageIndex,
                            left = overlay.left,
                            top = overlay.top,
                            width = overlay.width,
                            height = overlay.height,
                            bitmap = copy,
                        )
                    }
                    try {
                        val outFile = File(
                            context.cacheDir,
                            "firmado_${System.currentTimeMillis()}.pdf",
                        )
                        PdfSigner(context).applyStamps(uri, stamps, outFile)
                        outFile
                    } finally {
                        stampCopies.forEach { if (!it.isRecycled) it.recycle() }
                    }
                }
            }
            result.fold(
                onSuccess = { file ->
                    signedFile = file
                    exportState = SignExportState.Ready
                },
                onFailure = {
                    exportState = SignExportState.Editing
                    snackbar.showSnackbar(context.getString(R.string.sign_save_error))
                },
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF2F4F6),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sign_title),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge,
                    )
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
                },
                colors = androsTopAppBarColors(),
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarInsetPadding(),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                when (exportState) {
                    SignExportState.Ready -> {
                        val file = signedFile
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.sign_ready),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.sign_ready_title),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF5B403D),
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { file?.let(::shareSignedPdf) },
                                    enabled = file != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.sign_share))
                                }
                                Button(
                                    onClick = { file?.let(::downloadSignedPdf) },
                                    enabled = file != null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                ) {
                                    Icon(Icons.Outlined.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.sign_download))
                                }
                            }
                        }
                    }

                    else -> {
                        Button(
                            onClick = ::finalizeAndSave,
                            enabled = exportState != SignExportState.Processing &&
                                session != null &&
                                overlays.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            if (exportState == SignExportState.Processing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.sign_finish_save),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                )
                            }
                        }
                    }
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
                        text = stringResource(R.string.sign_error_open),
                        color = MaterialTheme.colorScheme.error,
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
                    CircularProgressIndicator(color = Primary)
                }
            }

            else -> {
                val doc = session!!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val viewportWidthPx = with(density) { maxWidth.toPx() }
                        val viewportHeightPx = with(density) { maxHeight.toPx() }
                        val isZoomed = scale > 1.01f

                        LazyColumn(
                            state = rememberLazyListState(),
                            userScrollEnabled = !isZoomed,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(viewportWidthPx, viewportHeightPx) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.size >= 2) {
                                                val zoomChange = event.calculateZoom()
                                                val panChange = event.calculatePan()
                                                val newScale = (latestScale * zoomChange)
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
                                .pointerInput(viewportWidthPx, viewportHeightPx, selectedOverlayId) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            val pressed = event.changes.filter { it.pressed }
                                            if (
                                                pressed.size == 1 &&
                                                latestScale > 1.01f &&
                                                selectedOverlayId == null &&
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
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 120.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(doc.pageCount, key = { it }) { pageIndex ->
                                SignPageItem(
                                    session = doc,
                                    pageIndex = pageIndex,
                                    viewScale = scale,
                                    overlays = overlays.filter { it.pageIndex == pageIndex },
                                    selectedOverlayId = selectedOverlayId,
                                    placementActive = pendingPlacement != null &&
                                        exportState == SignExportState.Editing,
                                    onSelectOverlay = { selectedOverlayId = it },
                                    onDeleteOverlay = { id ->
                                        overlays = overlays.filterNot { it.id == id }
                                        if (selectedOverlayId == id) selectedOverlayId = null
                                    },
                                    onMoveOverlay = { id, dx, dy ->
                                        overlays = overlays.map { overlay ->
                                            if (overlay.id != id) {
                                                overlay
                                            } else {
                                                overlay.copy(
                                                    left = (overlay.left + dx)
                                                        .coerceIn(0f, 1f - overlay.width),
                                                    top = (overlay.top + dy)
                                                        .coerceIn(0f, 1f - overlay.height),
                                                )
                                            }
                                        }
                                    },
                                    onResizeOverlay = { id, dWidthFrac, dHeightFrac ->
                                        overlays = overlays.map { overlay ->
                                            if (overlay.id != id) {
                                                overlay
                                            } else {
                                                val width = (overlay.width + dWidthFrac)
                                                    .coerceIn(0.08f, 0.9f)
                                                val height = (overlay.height + dHeightFrac)
                                                    .coerceIn(0.04f, 0.7f)
                                                overlay.copy(
                                                    left = overlay.left.coerceIn(0f, 1f - width),
                                                    top = overlay.top.coerceIn(0f, 1f - height),
                                                    width = width,
                                                    height = height,
                                                )
                                            }
                                        }
                                    },
                                    onPlaceAt = { left, top ->
                                        val pending = pendingPlacement
                                        if (pending is PendingPlacement.Stamp) {
                                            placeStamp(pageIndex, left, top, pending)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (exportState == SignExportState.Editing) {
                        SignToolPalette(
                            selectedTool = selectedTool,
                            enabled = true,
                            onToolClick = { tool ->
                                selectedTool = tool
                                when (tool) {
                                    SignTool.Signature -> showSignaturePad = true
                                    SignTool.Initials -> showInitialsPad = true
                                    SignTool.Text -> {
                                        textInput = ""
                                        showTextDialog = true
                                    }
                                    SignTool.Date -> {
                                        val dateText = SimpleDateFormat(
                                            "dd/MM/yyyy",
                                            Locale.getDefault(),
                                        ).format(Date())
                                        val bitmap = PdfSigner.textBitmap(dateText, textSizePx = 96f)
                                        pendingPlacement = PendingPlacement.Stamp(
                                            bitmap = bitmap,
                                            preferredWidthFrac = 0.28f,
                                        )
                                        scope.launch {
                                            snackbar.showSnackbar(
                                                context.getString(R.string.sign_tap_to_place),
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                        )
                    }

                    if (exportState == SignExportState.Processing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.sign_processing),
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSignaturePad) {
        SignaturePadSheet(
            title = stringResource(R.string.sign_draw_title),
            onDismiss = {
                showSignaturePad = false
                selectedTool = null
            },
            onSaved = { bitmap ->
                showSignaturePad = false
                pendingPlacement = PendingPlacement.Stamp(bitmap, preferredWidthFrac = 0.38f)
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.sign_tap_to_place))
                }
            },
        )
    }

    if (showInitialsPad) {
        SignaturePadSheet(
            title = stringResource(R.string.sign_draw_initials_title),
            onDismiss = {
                showInitialsPad = false
                selectedTool = null
            },
            onSaved = { bitmap ->
                showInitialsPad = false
                pendingPlacement = PendingPlacement.Stamp(bitmap, preferredWidthFrac = 0.18f)
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.sign_tap_to_place))
                }
            },
        )
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = {
                showTextDialog = false
                selectedTool = null
            },
            title = { Text(stringResource(R.string.sign_text_title)) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sign_text_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val value = textInput.trim()
                            if (value.isNotEmpty()) {
                                showTextDialog = false
                                val bitmap = PdfSigner.textBitmap(value, textSizePx = 88f)
                                pendingPlacement = PendingPlacement.Stamp(bitmap, 0.4f)
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(R.string.sign_tap_to_place),
                                    )
                                }
                            }
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = textInput.trim()
                        if (value.isNotEmpty()) {
                            showTextDialog = false
                            val bitmap = PdfSigner.textBitmap(value, textSizePx = 88f)
                            pendingPlacement = PendingPlacement.Stamp(bitmap, 0.4f)
                            scope.launch {
                                snackbar.showSnackbar(
                                    context.getString(R.string.sign_tap_to_place),
                                )
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.sign_place))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTextDialog = false
                        selectedTool = null
                    },
                ) {
                    Text(stringResource(R.string.sign_cancel))
                }
            },
        )
    }
}

@Composable
private fun SignToolPalette(
    selectedTool: SignTool?,
    enabled: Boolean,
    onToolClick: (SignTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.shadow(10.dp, RoundedCornerShape(999.dp)),
        color = Color(0xE6E0E3E5),
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolButton(
                icon = Icons.Outlined.Draw,
                label = stringResource(R.string.sign_tool_signature),
                selected = selectedTool == SignTool.Signature,
                enabled = enabled,
                onClick = { onToolClick(SignTool.Signature) },
            )
            PaletteDivider()
            ToolButton(
                icon = Icons.Outlined.Title,
                label = stringResource(R.string.sign_tool_text),
                selected = selectedTool == SignTool.Text,
                enabled = enabled,
                onClick = { onToolClick(SignTool.Text) },
            )
            PaletteDivider()
            ToolButton(
                icon = Icons.Outlined.Badge,
                label = stringResource(R.string.sign_tool_initials),
                selected = selectedTool == SignTool.Initials,
                enabled = enabled,
                onClick = { onToolClick(SignTool.Initials) },
            )
            PaletteDivider()
            ToolButton(
                icon = Icons.Outlined.Event,
                label = stringResource(R.string.sign_tool_date),
                selected = selectedTool == SignTool.Date,
                enabled = enabled,
                onClick = { onToolClick(SignTool.Date) },
            )
        }
    }
}

@Composable
private fun PaletteDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(24.dp)
            .background(Color(0x4DE4BEB9)),
    )
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) PrimaryContainer else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .size(width = 52.dp, height = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else Color(0xFF5B403D),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (selected) Color.White else Color(0xFF5B403D),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SignPageItem(
    session: PdfDocumentSession,
    pageIndex: Int,
    viewScale: Float = 1f,
    overlays: List<PageOverlay>,
    selectedOverlayId: String?,
    placementActive: Boolean,
    onSelectOverlay: (String) -> Unit,
    onDeleteOverlay: (String) -> Unit,
    onMoveOverlay: (id: String, dxFrac: Float, dyFrac: Float) -> Unit,
    onResizeOverlay: (id: String, dWidthFrac: Float, dHeightFrac: Float) -> Unit,
    onPlaceAt: (left: Float, top: Float) -> Unit,
) {
    var pageBitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    val density = LocalDensity.current
    val aspectRatio = remember(pageIndex, session) {
        runCatching { session.pageAspectRatio(pageIndex) }.getOrDefault(0.707f)
    }

    LaunchedEffect(session, pageIndex) {
        pageBitmap = withContext(Dispatchers.Default) {
            runCatching { session.renderPage(pageIndex, targetWidthPx = 1080) }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 3.dp,
        color = Color.White,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (placementActive) {
                        Modifier.pointerInput(pageIndex) {
                            detectTapGestures { offset ->
                                onPlaceAt(
                                    (offset.x / size.width).coerceIn(0f, 1f),
                                    (offset.y / size.height).coerceIn(0f, 1f),
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            val pageWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val pageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val rendered = pageBitmap

            if (rendered != null && !rendered.isRecycled) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                    strokeWidth = 2.dp,
                    color = Primary,
                )
            }

            if (placementActive) {
                Text(
                    text = stringResource(R.string.sign_here),
                    color = Primary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Primary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .border(1.dp, Primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            overlays.forEach { overlay ->
                val selected = overlay.id == selectedOverlayId
                val overlayWidthPx = overlay.width * pageWidthPx
                val overlayHeightPx = overlay.height * pageHeightPx
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (overlay.left * pageWidthPx).roundToInt(),
                                y = (overlay.top * pageHeightPx).roundToInt(),
                            )
                        }
                        .size(
                            width = with(density) { overlayWidthPx.toDp() },
                            height = with(density) { overlayHeightPx.toDp() },
                        )
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = if (selected) Primary else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .pointerInput(overlay.id, pageWidthPx, pageHeightPx) {
                            detectTapGestures(onTap = { onSelectOverlay(overlay.id) })
                        }
                        .pointerInput(overlay.id, pageWidthPx, pageHeightPx, viewScale) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onSelectOverlay(overlay.id)
                                val scaleFactor = viewScale.coerceAtLeast(1f)
                                onMoveOverlay(
                                    overlay.id,
                                    dragAmount.x / scaleFactor / pageWidthPx,
                                    dragAmount.y / scaleFactor / pageHeightPx,
                                )
                            }
                        },
                ) {
                    Image(
                        bitmap = overlay.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (selected) {
                        IconButton(
                            onClick = { onDeleteOverlay(overlay.id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-8).dp)
                                .size(28.dp)
                                .background(Primary, CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.sign_delete_overlay),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        // Width handle (right edge)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 10.dp)
                                .size(width = 18.dp, height = 28.dp)
                                .background(Primary, RoundedCornerShape(999.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(999.dp))
                                .pointerInput(overlay.id, pageWidthPx, viewScale) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val scaleFactor = viewScale.coerceAtLeast(1f)
                                        onResizeOverlay(
                                            overlay.id,
                                            dragAmount.x / scaleFactor / pageWidthPx,
                                            0f,
                                        )
                                    }
                                },
                        )
                        // Height handle (bottom edge)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = 10.dp)
                                .size(width = 28.dp, height = 18.dp)
                                .background(Primary, RoundedCornerShape(999.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(999.dp))
                                .pointerInput(overlay.id, pageHeightPx, viewScale) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val scaleFactor = viewScale.coerceAtLeast(1f)
                                        onResizeOverlay(
                                            overlay.id,
                                            0f,
                                            dragAmount.y / scaleFactor / pageHeightPx,
                                        )
                                    }
                                },
                        )
                        // Free resize (corner)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 10.dp, y = 10.dp)
                                .size(26.dp)
                                .background(Primary, CircleShape)
                                .border(2.dp, Color.White, CircleShape)
                                .pointerInput(overlay.id, pageWidthPx, pageHeightPx, viewScale) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val scaleFactor = viewScale.coerceAtLeast(1f)
                                        onResizeOverlay(
                                            overlay.id,
                                            dragAmount.x / scaleFactor / pageWidthPx,
                                            dragAmount.y / scaleFactor / pageHeightPx,
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.White, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}
