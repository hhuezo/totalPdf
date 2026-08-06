package com.hhuezo.pdfconverter.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hhuezo.pdfconverter.R
import com.hhuezo.pdfconverter.ui.theme.AndrosTheme

enum class QuickToolId {
    ToImage,
    Sign,
    DeletePages,
    RotatePages,
    Merge,
    Scan,
}

// Distinct soft tones so each tool is easy to spot at a glance.
private val ImageCardBg = Color(0xFFD8F3E5)
private val ImageIconTint = Color(0xFF1B6B45)
private val ImageText = Color(0xFF0E3D28)

private val SignCardBg = Color(0xFFFFE4CC)
private val SignIconTint = Color(0xFF9A4A12)
private val SignText = Color(0xFF5C2A08)

private val DeleteCardBg = Color(0xFFFFD6DE)
private val DeleteIconTint = Color(0xFF9B1B3A)
private val DeleteText = Color(0xFF5C0F22)

private val RotateCardBg = Color(0xFFFFF0CC)
private val RotateIconTint = Color(0xFF8A5A00)
private val RotateText = Color(0xFF4A3500)

private val MergeCardBg = Color(0xFFE6D9FF)
private val MergeIconTint = Color(0xFF5B2FA0)
private val MergeText = Color(0xFF321A63)

private val ScanCardBg = Color(0xFFD4F1F9)
private val ScanIconTint = Color(0xFF0E6B7A)
private val ScanText = Color(0xFF08434C)

private data class QuickTool(
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val cardBackground: Color,
    val iconBackground: Color,
    val iconTint: Color,
    val titleColor: Color,
    val subtitleColor: Color,
)

@Composable
fun ToolsScreen(
    onToolClick: (QuickToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools = listOf(
        QuickTool(
            titleRes = R.string.pdf_to_image,
            subtitleRes = R.string.pdf_to_image_subtitle,
            icon = Icons.Outlined.Image,
            cardBackground = ImageCardBg,
            iconBackground = Color.White.copy(alpha = 0.9f),
            iconTint = ImageIconTint,
            titleColor = ImageText,
            subtitleColor = ImageText.copy(alpha = 0.72f),
        ) to QuickToolId.ToImage,
        QuickTool(
            titleRes = R.string.delete_pages,
            subtitleRes = R.string.delete_pages_subtitle,
            icon = Icons.Outlined.DeleteSweep,
            cardBackground = DeleteCardBg,
            iconBackground = Color.White.copy(alpha = 0.9f),
            iconTint = DeleteIconTint,
            titleColor = DeleteText,
            subtitleColor = DeleteText.copy(alpha = 0.72f),
        ) to QuickToolId.DeletePages,
        QuickTool(
            titleRes = R.string.rotate_pages_title,
            subtitleRes = R.string.rotate_pages_subtitle,
            icon = Icons.Outlined.Rotate90DegreesCw,
            cardBackground = RotateCardBg,
            iconBackground = Color.White.copy(alpha = 0.9f),
            iconTint = RotateIconTint,
            titleColor = RotateText,
            subtitleColor = RotateText.copy(alpha = 0.72f),
        ) to QuickToolId.RotatePages,
        QuickTool(
            titleRes = R.string.merge_pdfs,
            subtitleRes = R.string.merge_pdfs_subtitle,
            icon = Icons.Outlined.MergeType,
            cardBackground = MergeCardBg,
            iconBackground = Color.White.copy(alpha = 0.9f),
            iconTint = MergeIconTint,
            titleColor = MergeText,
            subtitleColor = MergeText.copy(alpha = 0.72f),
        ) to QuickToolId.Merge,
        QuickTool(
            titleRes = R.string.scan_to_pdf,
            subtitleRes = R.string.scan_to_pdf_subtitle,
            icon = Icons.Outlined.DocumentScanner,
            cardBackground = ScanCardBg,
            iconBackground = Color.White.copy(alpha = 0.9f),
            iconTint = ScanIconTint,
            titleColor = ScanText,
            subtitleColor = ScanText.copy(alpha = 0.72f),
        ) to QuickToolId.Scan,
        QuickTool(
            titleRes = R.string.sign_pdf,
            subtitleRes = R.string.sign_pdf_subtitle,
            icon = Icons.Outlined.Draw,
            cardBackground = SignCardBg,
            iconBackground = Color.White.copy(alpha = 0.9f),
            iconTint = SignIconTint,
            titleColor = SignText,
            subtitleColor = SignText.copy(alpha = 0.72f),
        ) to QuickToolId.Sign,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            tools.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    row.forEach { (tool, id) ->
                        ToolCard(
                            tool = tool,
                            onClick = { onToolClick(id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: QuickTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(tool.cardBackground)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tool.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = tool.iconTint,
            )
        }
        Column {
            Text(
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = tool.titleColor,
            )
            Text(
                text = stringResource(tool.subtitleRes),
                style = MaterialTheme.typography.labelSmall,
                color = tool.subtitleColor,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolsScreenPreview() {
    AndrosTheme {
        ToolsScreen(onToolClick = {})
    }
}
