package com.hhuezo.pdfconverter.ui.deletepages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.pdf.PdfBlankPageDetector
import com.hhuezo.pdfconverter.pdf.PdfDocumentSession
import com.hhuezo.pdfconverter.pdf.PdfPageRemover
import com.hhuezo.pdfconverter.util.PdfFileSaver
import com.hhuezo.pdfconverter.util.queryPdfInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class DeleteUiState {
    Idle,
    Processing,
    Ready,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDeletePagesScreen(
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
    var selectedPages by remember { mutableStateOf(setOf<Int>()) }
    var blankPages by remember(uri) { mutableStateOf(setOf<Int>()) }
    var isDetectingBlanks by remember { mutableStateOf(false) }
    var uiState by remember { mutableStateOf(DeleteUiState.Idle) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var removedCount by remember { mutableIntStateOf(0) }

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
                    displayName = "${baseName}_sin_paginas.pdf",
                )
            }
            if (saved != null) {
                snackbar.showSnackbar(context.getString(R.string.delete_pages_save_success))
            } else {
                snackbar.showSnackbar(context.getString(R.string.delete_pages_download_error))
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
                snackbar.showSnackbar(context.getString(R.string.delete_pages_download_error))
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

    fun togglePage(pageIndex: Int) {
        if (uiState == DeleteUiState.Processing) return
        selectedPages = if (pageIndex in selectedPages) {
            selectedPages - pageIndex
        } else {
            selectedPages + pageIndex
        }
        uiState = DeleteUiState.Idle
        outputFile = null
    }

    fun selectBlankPages() {
        val doc = session ?: return
        if (isDetectingBlanks || uiState == DeleteUiState.Processing) return
        isDetectingBlanks = true
        scope.launch {
            val detected = withContext(Dispatchers.Default) {
                runCatching { PdfBlankPageDetector.findBlankPages(doc) }.getOrElse { emptyList() }
            }
            blankPages = detected.toSet()
            isDetectingBlanks = false

            when {
                detected.isEmpty() -> {
                    snackbar.showSnackbar(context.getString(R.string.delete_pages_no_blank))
                }
                else -> {
                    val maxSelectable = doc.pageCount - 1
                    var newSelection = selectedPages + detected.toSet()
                    val trimmed = newSelection.size > maxSelectable
                    if (trimmed) {
                        val pageToKeep = (0 until doc.pageCount)
                            .firstOrNull { it !in blankPages }
                            ?: detected.minOrNull()
                            ?: 0
                        newSelection = newSelection - pageToKeep
                        if (newSelection.size > maxSelectable) {
                            newSelection = newSelection.sorted().take(maxSelectable).toSet()
                        }
                    }
                    selectedPages = newSelection
                    uiState = DeleteUiState.Idle
                    outputFile = null
                    snackbar.showSnackbar(
                        if (trimmed) {
                            context.getString(R.string.delete_pages_blank_partial)
                        } else {
                            context.getString(
                                R.string.delete_pages_blank_selected,
                                detected.size,
                            )
                        },
                    )
                }
            }
        }
    }

    fun runDelete() {
        val doc = session ?: return
        if (uiState == DeleteUiState.Processing) return
        when {
            selectedPages.isEmpty() -> {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.delete_pages_error_none))
                }
            }
            selectedPages.size >= doc.pageCount -> {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.delete_pages_error_all))
                }
            }
            else -> {
                val pages = selectedPages.sorted()
                uiState = DeleteUiState.Processing
                outputFile = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = File(
                                context.cacheDir,
                                "sin_paginas_${System.currentTimeMillis()}.pdf",
                            )
                            PdfPageRemover(context).removePages(
                                uri = uri,
                                pageIndicesToRemove = pages,
                                outputFile = out,
                            )
                            out to pages.size
                        }
                    }
                    result.fold(
                        onSuccess = { (file, count) ->
                            outputFile = file
                            removedCount = count
                            uiState = DeleteUiState.Ready
                        },
                        onFailure = {
                            uiState = DeleteUiState.Idle
                            snackbar.showSnackbar(
                                context.getString(R.string.delete_pages_error),
                            )
                        },
                    )
                }
            }
        }
    }

    val pageCount = session?.pageCount ?: 0
    val remainingCount = (pageCount - selectedPages.size).coerceAtLeast(0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.delete_pages_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pageCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.delete_pages_selection_meta,
                                    selectedPages.size,
                                    remainingCount,
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
                if (uiState == DeleteUiState.Ready) {
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
                                    R.string.delete_pages_ready,
                                    removedCount,
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
                                Text(stringResource(R.string.delete_pages_share))
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
                                Text(stringResource(R.string.delete_pages_download))
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = ::runDelete,
                        enabled = !openError && pageCount > 1 &&
                            uiState != DeleteUiState.Processing &&
                            selectedPages.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        if (uiState == DeleteUiState.Processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.delete_pages_processing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedPages.isEmpty()) {
                                    stringResource(R.string.delete_pages_action)
                                } else {
                                    stringResource(
                                        R.string.delete_pages_action_count,
                                        selectedPages.size,
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
                        text = stringResource(R.string.delete_pages_error_open),
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

            pageCount == 1 -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.delete_pages_single_page),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.delete_pages_tap_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FilledTonalButton(
                                    onClick = ::selectBlankPages,
                                    enabled = uiState != DeleteUiState.Processing && !isDetectingBlanks,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(50),
                                ) {
                                    if (isDetectingBlanks) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.delete_pages_detecting_blank))
                                    } else {
                                        Icon(Icons.Outlined.FilterNone, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.delete_pages_select_blank))
                                    }
                                }
                            }
                        }
                        items(doc.pageCount, key = { it }) { pageIndex ->
                            SelectablePdfPage(
                                session = doc,
                                pageIndex = pageIndex,
                                selected = pageIndex in selectedPages,
                                isBlank = pageIndex in blankPages,
                                targetWidthPx = renderWidthPx,
                                enabled = uiState != DeleteUiState.Processing,
                                onToggle = { togglePage(pageIndex) },
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
private fun SelectablePdfPage(
    session: PdfDocumentSession,
    pageIndex: Int,
    selected: Boolean,
    isBlank: Boolean,
    targetWidthPx: Int,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    val aspectRatio = remember(pageIndex, session) {
        runCatching { session.pageAspectRatio(pageIndex) }.getOrDefault(0.707f)
    }
    val pageShape = RoundedCornerShape(4.dp)
    val errorColor = MaterialTheme.colorScheme.error

    LaunchedEffect(session, pageIndex, targetWidthPx) {
        if (targetWidthPx <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.Default) {
            runCatching { session.renderPage(pageIndex, targetWidthPx) }.getOrNull()
        }
    }

    Surface(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clickable(enabled = enabled, onClick = onToggle),
        shape = pageShape,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 0.dp,
            color = if (selected) errorColor else Color.Transparent,
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
                        R.string.delete_pages_page_label,
                        pageIndex + 1,
                    ),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(errorColor.copy(alpha = 0.22f)),
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
                    text = stringResource(R.string.delete_pages_page_label, pageIndex + 1),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            if (isBlank) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
                    shadowElevation = 1.dp,
                ) {
                    Text(
                        text = stringResource(R.string.delete_pages_blank_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            IconButton(
                onClick = onToggle,
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = if (selected) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = stringResource(
                        if (selected) {
                            R.string.delete_pages_unmark
                        } else {
                            R.string.delete_pages_mark
                        },
                    ),
                    tint = if (selected) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
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
            context.getString(R.string.delete_pages_share_title),
        ),
    )
}
