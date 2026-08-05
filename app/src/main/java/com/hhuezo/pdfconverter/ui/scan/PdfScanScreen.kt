package com.hhuezo.pdfconverter.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.pdf.ImagesToPdf
import com.hhuezo.pdfconverter.util.PdfFileSaver
import com.hhuezo.pdfconverter.util.loadOrientedBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ScanUiState {
    Idle,
    Processing,
    Ready,
}

private val PathListSaver = listSaver<MutableList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfScanScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val pagePaths = rememberSaveable(saver = PathListSaver) {
        mutableListOf<String>().toMutableStateList()
    }
    var uiState by rememberSaveable { mutableStateOf(ScanUiState.Idle) }
    var outputPath by rememberSaveable { mutableStateOf<String?>(null) }

    val outputFile = remember(outputPath) { outputPath?.let(::File) }
    val activity = context as? Activity

    fun createScanFile(): File {
        val dir = File(context.cacheDir, "scan_pages").apply { mkdirs() }
        return File(dir, "scan_${System.currentTimeMillis()}.jpg")
    }

    fun resetOutput() {
        uiState = ScanUiState.Idle
        outputPath = null
    }

    fun addPagePath(path: String) {
        if (path !in pagePaths) {
            pagePaths.add(path)
            resetOutput()
        }
    }

    fun copyUriToScanCache(uri: Uri): String? {
        return runCatching {
            val dest = createScanFile()
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (dest.length() > 0L) dest.absolutePath else null
        }.getOrNull()
    }

    val documentScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        // Si el usuario descarta en el escáner de Google, no tocamos las páginas
        // que ya estaban guardadas en esta pantalla.
        if (activityResult.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val pages = scanResult?.pages.orEmpty()
        if (pages.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                pages.mapNotNull { page -> copyUriToScanCache(page.imageUri) }
            }
            if (copied.isEmpty()) {
                snackbar.showSnackbar(context.getString(R.string.scan_error_images))
            } else {
                copied.forEach(::addPagePath)
            }
        }
    }

    fun launchDocumentScanner() {
        val host = activity
        if (host == null) {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.scan_error_scanner))
            }
            return
        }
        if (pagePaths.size >= 30) {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.scan_error_page_limit))
            }
            return
        }
        // Una página por sesión: si descartan a mitad, no se pierden las ya confirmadas.
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(host)
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build(),
                )
            }
            .addOnFailureListener {
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.scan_error_scanner))
                }
            }
    }

    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                uris.mapNotNull(::copyUriToScanCache)
            }
            if (copied.isEmpty()) {
                snackbar.showSnackbar(context.getString(R.string.scan_error_images))
            } else {
                copied.forEach(::addPagePath)
            }
        }
    }

    fun downloadResult(file: File) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                PdfFileSaver.saveToDownloads(
                    context = context,
                    source = file,
                    displayName = "escaneado_${System.currentTimeMillis()}.pdf",
                )
            }
            if (saved != null) {
                snackbar.showSnackbar(context.getString(R.string.scan_save_success))
            } else {
                snackbar.showSnackbar(context.getString(R.string.scan_download_error))
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
                snackbar.showSnackbar(context.getString(R.string.scan_download_error))
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

    fun movePage(from: Int, to: Int) {
        if (to !in pagePaths.indices) return
        val item = pagePaths.removeAt(from)
        pagePaths.add(to, item)
        resetOutput()
    }

    fun runCreatePdf() {
        if (uiState == ScanUiState.Processing) return
        if (pagePaths.isEmpty()) {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.scan_error_empty))
            }
            return
        }
        uiState = ScanUiState.Processing
        outputPath = null
        val uris = pagePaths.map { Uri.fromFile(File(it)) }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val out = File(
                        context.cacheDir,
                        "escaneado_${System.currentTimeMillis()}.pdf",
                    )
                    ImagesToPdf(context).create(uris, out)
                    out
                }
            }
            result.fold(
                onSuccess = { file ->
                    outputPath = file.absolutePath
                    uiState = ScanUiState.Ready
                },
                onFailure = { error ->
                    android.util.Log.e(
                        "PdfKitProScan",
                        "No se pudo crear el PDF: ${error.message}",
                        error,
                    )
                    uiState = ScanUiState.Idle
                    snackbar.showSnackbar(context.getString(R.string.scan_error))
                },
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.scan_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (pagePaths.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.scan_pages_meta, pagePaths.size),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState == ScanUiState.Ready) {
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
                                    R.string.scan_ready,
                                    pagePaths.size,
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
                                Text(stringResource(R.string.scan_share))
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
                                Text(stringResource(R.string.scan_download))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { launchDocumentScanner() },
                                enabled = uiState != ScanUiState.Processing,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.scan_camera))
                            }
                            OutlinedButton(
                                onClick = { pickImages.launch("image/*") },
                                enabled = uiState != ScanUiState.Processing,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(50),
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.scan_gallery))
                            }
                        }
                        Button(
                            onClick = ::runCreatePdf,
                            enabled = pagePaths.isNotEmpty() &&
                                uiState != ScanUiState.Processing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            if (uiState == ScanUiState.Processing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.scan_processing),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            } else {
                                Icon(Icons.Outlined.DocumentScanner, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.scan_create),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (pagePaths.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.scan_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.scan_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = pagePaths,
                    key = { index, path -> "$path-$index" },
                ) { index, path ->
                    ScanPageCard(
                        path = path,
                        pageNumber = index + 1,
                        canMoveLeft = index > 0 && uiState != ScanUiState.Processing,
                        canMoveRight = index < pagePaths.lastIndex &&
                            uiState != ScanUiState.Processing,
                        enabled = uiState != ScanUiState.Processing,
                        onMoveLeft = { movePage(index, index - 1) },
                        onMoveRight = { movePage(index, index + 1) },
                        onRemove = {
                            pagePaths.removeAt(index)
                            File(path).delete()
                            resetOutput()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanPageCard(
    path: String,
    pageNumber: Int,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    enabled: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit,
) {
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            loadOrientedBitmap(path, maxSidePx = 512)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val current = bitmap
            if (current != null && !current.isRecycled) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = stringResource(R.string.scan_page_label, pageNumber),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Text(
                    text = pageNumber.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(50),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.scan_remove_page),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onMoveLeft,
                enabled = canMoveLeft && enabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.scan_move_left),
                )
            }
            Text(
                text = stringResource(R.string.scan_page_label, pageNumber),
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = onMoveRight,
                enabled = canMoveRight && enabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.scan_move_right),
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
            context.getString(R.string.scan_share_title),
        ),
    )
}
