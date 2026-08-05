package com.hhuezo.pdfconverter.ui.merge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MergeType
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.data.RecentPdfsRepository
import com.hhuezo.pdfconverter.pdf.PdfMerger
import com.hhuezo.pdfconverter.ui.theme.androsTopAppBarColors
import com.hhuezo.pdfconverter.ui.theme.navigationBarInsetPadding
import com.hhuezo.pdfconverter.ui.theme.PrimaryFixedDim
import com.hhuezo.pdfconverter.util.PdfFileSaver
import com.hhuezo.pdfconverter.util.formatFileSize
import com.hhuezo.pdfconverter.util.queryPdfInfo
import com.hhuezo.pdfconverter.util.takePersistableReadPermission
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class MergeUiState {
    Idle,
    Processing,
    Ready,
}

private data class MergePdfItem(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
)

private val MergePdfItemSaver = listSaver<MutableList<MergePdfItem>, Any>(
    save = { list ->
        list.flatMap { item ->
            listOf(item.uriString, item.displayName, item.sizeBytes, item.pageCount)
        }
    },
    restore = { flat ->
        flat.chunked(4).mapNotNull { chunk ->
            if (chunk.size != 4) return@mapNotNull null
            MergePdfItem(
                uriString = chunk[0] as String,
                displayName = chunk[1] as String,
                sizeBytes = (chunk[2] as Number).toLong(),
                pageCount = (chunk[3] as Number).toInt(),
            )
        }.toMutableStateList()
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMergeScreen(
    onBack: () -> Unit,
    initialUris: List<Uri> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val repository = remember { RecentPdfsRepository(context.applicationContext) }

    val pdfItems = rememberSaveable(saver = MergePdfItemSaver) {
        mutableListOf<MergePdfItem>().toMutableStateList()
    }
    var uiState by rememberSaveable { mutableStateOf(MergeUiState.Idle) }
    var outputPath by rememberSaveable { mutableStateOf<String?>(null) }
    var mergedPageCount by rememberSaveable { mutableIntStateOf(0) }
    var didSeedInitial by rememberSaveable { mutableStateOf(false) }

    val outputFile = remember(outputPath) { outputPath?.let(::File) }

    suspend fun loadItems(uris: List<Uri>): List<MergePdfItem> {
        return withContext(Dispatchers.IO) {
            uris.mapNotNull { uri ->
                runCatching {
                    context.takePersistableReadPermission(uri)
                    val info = context.queryPdfInfo(uri)
                    val pages = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { it.pageCount }
                    } ?: 0
                    MergePdfItem(
                        uriString = uri.toString(),
                        displayName = info.displayName,
                        sizeBytes = info.sizeBytes,
                        pageCount = pages,
                    ).also {
                        repository.addOrUpdate(
                            uri = it.uriString,
                            displayName = it.displayName,
                            sizeBytes = it.sizeBytes,
                        )
                    }
                }.getOrNull()
            }
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val existing = pdfItems.map { it.uriString }.toSet()
            val fresh = uris.filter { it.toString() !in existing }
            if (fresh.isEmpty()) {
                snackbar.showSnackbar(context.getString(R.string.merge_already_added))
                return@launch
            }
            val loaded = loadItems(fresh)
            if (loaded.isEmpty()) {
                snackbar.showSnackbar(context.getString(R.string.merge_error_open))
                return@launch
            }
            pdfItems.addAll(loaded)
            uiState = MergeUiState.Idle
            outputPath = null
        }
    }

    val pickPdfs = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        addUris(uris)
    }

    LaunchedEffect(initialUris) {
        if (!didSeedInitial && initialUris.isNotEmpty() && pdfItems.isEmpty()) {
            didSeedInitial = true
            addUris(initialUris)
        } else if (!didSeedInitial && initialUris.isEmpty() && pdfItems.isEmpty()) {
            didSeedInitial = true
            pickPdfs.launch(arrayOf("application/pdf"))
        }
    }

    fun downloadResult(file: File) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                PdfFileSaver.saveToDownloads(
                    context = context,
                    source = file,
                    displayName = "unido_${System.currentTimeMillis()}.pdf",
                )
            }
            if (saved != null) {
                snackbar.showSnackbar(context.getString(R.string.merge_save_success))
            } else {
                snackbar.showSnackbar(context.getString(R.string.merge_download_error))
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
                snackbar.showSnackbar(context.getString(R.string.merge_download_error))
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

    fun moveItem(from: Int, to: Int) {
        if (to !in pdfItems.indices) return
        val item = pdfItems.removeAt(from)
        pdfItems.add(to, item)
        uiState = MergeUiState.Idle
        outputPath = null
    }

    fun runMerge() {
        if (uiState == MergeUiState.Processing) return
        if (pdfItems.size < 2) {
            scope.launch {
                snackbar.showSnackbar(context.getString(R.string.merge_error_min))
            }
            return
        }
        uiState = MergeUiState.Processing
        outputPath = null
        val uris = pdfItems.map { Uri.parse(it.uriString) }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val out = File(
                        context.cacheDir,
                        "unido_${System.currentTimeMillis()}.pdf",
                    )
                    PdfMerger(context).merge(uris, out)
                    val pages = context.contentResolver.openFileDescriptor(
                        Uri.fromFile(out),
                        "r",
                    )?.use { pfd ->
                        PdfRenderer(pfd).use { it.pageCount }
                    } ?: pdfItems.sumOf { it.pageCount }
                    out to pages
                }
            }
            result.fold(
                onSuccess = { (file, pages) ->
                    outputPath = file.absolutePath
                    mergedPageCount = pages
                    uiState = MergeUiState.Ready
                },
                onFailure = {
                    uiState = MergeUiState.Idle
                    snackbar.showSnackbar(context.getString(R.string.merge_error))
                },
            )
        }
    }

    val totalPages = pdfItems.sumOf { it.pageCount }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.merge_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (pdfItems.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.merge_meta,
                                    pdfItems.size,
                                    totalPages,
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
                actions = {
                    IconButton(
                        onClick = { pickPdfs.launch(arrayOf("application/pdf")) },
                        enabled = uiState != MergeUiState.Processing,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.merge_add),
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
                if (uiState == MergeUiState.Ready) {
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
                                    R.string.merge_ready,
                                    mergedPageCount,
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
                                Text(stringResource(R.string.merge_share))
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
                                Text(stringResource(R.string.merge_download))
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = ::runMerge,
                        enabled = pdfItems.size >= 2 && uiState != MergeUiState.Processing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        if (uiState == MergeUiState.Processing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.merge_processing),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.MergeType, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.merge_action),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (pdfItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.merge_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { pickPdfs.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.merge_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.merge_order_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                itemsIndexed(
                    items = pdfItems,
                    key = { index, item -> "${item.uriString}-$index" },
                ) { index, item ->
                    MergeFileCard(
                        index = index,
                        item = item,
                        canMoveUp = index > 0 && uiState != MergeUiState.Processing,
                        canMoveDown = index < pdfItems.lastIndex &&
                            uiState != MergeUiState.Processing,
                        enabled = uiState != MergeUiState.Processing,
                        onMoveUp = { moveItem(index, index - 1) },
                        onMoveDown = { moveItem(index, index + 1) },
                        onRemove = {
                            pdfItems.removeAt(index)
                            uiState = MergeUiState.Idle
                            outputPath = null
                        },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { pickPdfs.launch(arrayOf("application/pdf")) },
                        enabled = uiState != MergeUiState.Processing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.merge_add_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeFileCard(
    index: Int,
    item: MergePdfItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PrimaryFixedDim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = (index + 1).toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.merge_file_meta,
                    formatFileSize(item.sizeBytes),
                    item.pageCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp && enabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.merge_move_up),
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown && enabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.merge_move_down),
                )
            }
        }

        IconButton(
            onClick = onRemove,
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.merge_remove),
                tint = MaterialTheme.colorScheme.error,
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
            context.getString(R.string.merge_share_title),
        ),
    )
}
