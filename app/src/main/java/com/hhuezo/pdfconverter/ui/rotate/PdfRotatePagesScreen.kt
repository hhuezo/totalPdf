package com.hhuezo.pdfconverter.ui.rotate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.pdf.PdfDocumentSession
import com.hhuezo.pdfconverter.pdf.PdfPageRotator
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.util.PdfFileSaver
import com.hhuezo.pdfconverter.util.queryPdfInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class RotateUiState {
    Idle,
    Processing,
    Ready,
}

private fun normalizeDegrees(degrees: Int): Int = ((degrees % 360) + 360) % 360

private fun previewRotationZ(degrees: Int): Float = when (normalizeDegrees(degrees)) {
    90 -> 90f
    180 -> 180f
    270 -> -90f
    else -> 0f
}

private fun swapsOrientation(degrees: Int): Boolean {
    val normalized = normalizeDegrees(degrees)
    return normalized == 90 || normalized == 270
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfRotatePagesScreen(
    uri: Uri,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snackbar = remember { SnackbarHostState() }

    val fileInfo = remember(uri) { context.queryPdfInfo(uri) }
    var session by remember { mutableStateOf<PdfDocumentSession?>(null) }
    var openError by remember { mutableStateOf(false) }
    var pageRotations by remember { mutableStateOf(mapOf<Int, Int>()) }
    var activePageIndex by remember { mutableIntStateOf(-1) }
    var uiState by remember { mutableStateOf(RotateUiState.Idle) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var rotatedCount by remember { mutableIntStateOf(0) }
    var totalPageCount by remember { mutableIntStateOf(0) }
    var savedToDownloads by remember { mutableStateOf(false) }

    DisposableEffect(uri) {
        val opened = runCatching { PdfDocumentSession(context, uri) }.getOrElse {
            openError = true
            null
        }
        session = opened
        onDispose {
            opened?.close()
            session = null
        }
    }

    fun downloadResult(file: File) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val baseName = fileInfo.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
                PdfFileSaver.saveToDownloads(
                    context = context,
                    source = file,
                    displayName = "${baseName}_rotado.pdf",
                )
            }
            if (saved != null) {
                savedToDownloads = true
                snackbar.showSnackbar(context.getString(R.string.rotate_pages_save_success))
            } else {
                snackbar.showSnackbar(context.getString(R.string.rotate_pages_download_error))
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val file = outputFile
        if (granted && file != null) {
            downloadResult(file)
        } else {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.rotate_pages_download_error))
            }
        }
    }

    fun requestDownload() {
        val file = outputFile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadResult(file)
            return
        }
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            downloadResult(file)
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    fun adjustPageRotation(pageIndex: Int, delta: Int) {
        if (uiState == RotateUiState.Processing) return
        activePageIndex = pageIndex
        val current = pageRotations[pageIndex] ?: 0
        val updated = normalizeDegrees(current + delta)
        pageRotations = if (updated == 0) {
            pageRotations - pageIndex
        } else {
            pageRotations + (pageIndex to updated)
        }
        uiState = RotateUiState.Idle
        outputFile = null
        savedToDownloads = false
    }

    fun resetPageRotation(pageIndex: Int) {
        if (uiState == RotateUiState.Processing) return
        activePageIndex = pageIndex
        pageRotations = pageRotations - pageIndex
        uiState = RotateUiState.Idle
        outputFile = null
        savedToDownloads = false
    }

    fun saveFullPdf() {
        if (session == null) return
        if (uiState == RotateUiState.Processing) return
        val changes = pageRotations.filter { normalizeDegrees(it.value) != 0 }
        when {
            changes.isEmpty() -> {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.rotate_pages_error_none))
                }
            }
            else -> {
                uiState = RotateUiState.Processing
                outputFile = null
                savedToDownloads = false
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = File(
                                context.cacheDir,
                                "rotado_${System.currentTimeMillis()}.pdf",
                            )
                            val pages = PdfPageRotator(context).rotatePages(
                                uri = uri,
                                pageRotations = changes,
                                outputFile = out,
                            )
                            Triple(out, changes.size, pages)
                        }
                    }
                    result.fold(
                        onSuccess = { (file, modifiedCount, pages) ->
                            outputFile = file
                            rotatedCount = modifiedCount
                            totalPageCount = pages
                            uiState = RotateUiState.Ready
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                downloadResult(file)
                            } else {
                                val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        permission,
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    downloadResult(file)
                                } else {
                                    storagePermissionLauncher.launch(permission)
                                }
                            }
                        },
                        onFailure = {
                            uiState = RotateUiState.Idle
                            snackbar.showSnackbar(context.getString(R.string.rotate_pages_error))
                        },
                    )
                }
            }
        }
    }

    val pageCount = session?.pageCount ?: 0
    val modifiedCount = pageRotations.count { normalizeDegrees(it.value) != 0 }
    val unchangedCount = (pageCount - modifiedCount).coerceAtLeast(0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.rotate_pages_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pageCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.rotate_pages_selection_meta,
                                    modifiedCount,
                                    unchangedCount,
                                ),
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
                colors = androsTopAppBarColors(),
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarInsetPadding(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
            ) {
                if (uiState == RotateUiState.Ready) {
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
                            Text(
                                text = stringResource(
                                    R.string.rotate_pages_ready,
                                    totalPageCount,
                                    rotatedCount,
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { outputFile?.let { sharePdf(context, it) } },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.rotate_pages_share))
                            }
                            Button(
                                onClick = { requestDownload() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Outlined.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.rotate_pages_download))
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = ::saveFullPdf,
                        enabled = !openError && pageCount > 0 &&
                            uiState != RotateUiState.Processing &&
                            modifiedCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        if (uiState == RotateUiState.Processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.rotate_pages_processing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Icon(Icons.Outlined.Rotate90DegreesCw, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (modifiedCount == 0) {
                                    stringResource(R.string.rotate_pages_save_full)
                                } else {
                                    stringResource(
                                        R.string.rotate_pages_save_full_count,
                                        modifiedCount,
                                    )
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
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
                        text = stringResource(R.string.rotate_pages_error_open),
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            else -> {
                val doc = session!!
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    val renderWidthPx = with(density) {
                        maxWidth.toPx().roundToInt().coerceAtLeast(1)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.rotate_pages_tap_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.rotate_pages_info),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }
                        }
                        items(doc.pageCount, key = { it }) { pageIndex ->
                            val rotationDegrees = pageRotations[pageIndex] ?: 0
                            RotatePageCard(
                                session = doc,
                                pageIndex = pageIndex,
                                rotationDegrees = rotationDegrees,
                                isHighlighted = pageIndex == activePageIndex || rotationDegrees != 0,
                                targetWidthPx = renderWidthPx,
                                enabled = uiState != RotateUiState.Processing,
                                onRotateCcw = { adjustPageRotation(pageIndex, 270) },
                                onRotateCw = { adjustPageRotation(pageIndex, 90) },
                                onRotate180 = { adjustPageRotation(pageIndex, 180) },
                                onReset = { resetPageRotation(pageIndex) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RotatePageCard(
    session: PdfDocumentSession,
    pageIndex: Int,
    rotationDegrees: Int,
    isHighlighted: Boolean,
    targetWidthPx: Int,
    enabled: Boolean,
    onRotateCcw: () -> Unit,
    onRotateCw: () -> Unit,
    onRotate180: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    val aspectRatio = remember(pageIndex, session) {
        runCatching { session.pageAspectRatio(pageIndex) }.getOrDefault(0.707f)
    }
    val isModified = normalizeDegrees(rotationDegrees) != 0
    val displayAspectRatio = if (swapsOrientation(rotationDegrees)) {
        1f / aspectRatio
    } else {
        aspectRatio
    }
    val pageShape = RoundedCornerShape(16.dp)
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(session, pageIndex, targetWidthPx) {
        if (targetWidthPx <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            runCatching { session.renderPage(pageIndex, targetWidthPx) }.getOrNull()
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isHighlighted) {
                    accentColor.copy(alpha = 0.06f)
                } else {
                    Color.Transparent
                },
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(displayAspectRatio),
            shape = pageShape,
            shadowElevation = if (isHighlighted) 4.dp else 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(
                width = if (isHighlighted) 2.dp else 0.dp,
                color = if (isHighlighted) accentColor else Color.Transparent,
            ),
        ) {
            Box(
                modifier = Modifier.clip(pageShape),
                contentAlignment = Alignment.Center,
            ) {
                val current = bitmap
                if (current != null && !current.isRecycled) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = stringResource(
                            R.string.rotate_pages_page_label,
                            pageIndex + 1,
                        ),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = previewRotationZ(rotationDegrees) },
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }

                if (isHighlighted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(accentColor.copy(alpha = 0.22f)),
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 1.dp,
                ) {
                    Text(
                        text = stringResource(R.string.rotate_pages_page_label, pageIndex + 1),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (isModified) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.rotate_pages_degrees_badge,
                                normalizeDegrees(rotationDegrees),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = if (isHighlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            tonalElevation = if (isHighlighted) 2.dp else 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RotationActionButton(
                    label = stringResource(R.string.rotate_pages_ccw_short),
                    icon = Icons.Outlined.Rotate90DegreesCcw,
                    onClick = onRotateCcw,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                RotationActionButton(
                    label = stringResource(R.string.rotate_pages_cw_short),
                    icon = Icons.Outlined.Rotate90DegreesCw,
                    onClick = onRotateCw,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
                RotationActionButton(
                    label = stringResource(R.string.rotate_pages_180_short),
                    icon = Icons.Outlined.SwapVert,
                    onClick = onRotate180,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (isModified) {
            TextButton(
                onClick = onReset,
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = stringResource(R.string.rotate_pages_reset),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.rotate_pages_reset),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RotationActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun sharePdf(context: android.content.Context, file: File) {
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
            context.getString(R.string.rotate_pages_share_title),
        ),
    )
}
