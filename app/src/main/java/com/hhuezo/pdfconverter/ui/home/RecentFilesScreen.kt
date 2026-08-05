package com.hhuezo.pdfconverter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hhuezo.pdfconverter.R

@Composable
fun RecentFilesScreen(
    recentFiles: List<RecentPdfFile>,
    onFileClick: (RecentPdfFile) -> Unit,
    onFileRemove: (RecentPdfFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (recentFiles.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            )
            Text(
                text = stringResource(R.string.no_recent_files),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.no_recent_files_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(recentFiles, key = { it.id }) { file ->
                RecentFileRow(
                    file = file,
                    onClick = { onFileClick(file) },
                    onRemove = { onFileRemove(file) },
                )
            }
        }
    }
}
