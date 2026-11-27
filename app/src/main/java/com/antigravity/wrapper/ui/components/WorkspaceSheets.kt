package com.antigravity.wrapper.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wrapper.data.DiffFile
import com.antigravity.wrapper.data.DiffLineKind
import com.antigravity.wrapper.data.DiffReviewData
import com.antigravity.wrapper.data.DocumentViewerData
import com.antigravity.wrapper.data.ProjectOption
import com.antigravity.wrapper.data.WorkspaceArtifactItem
import com.antigravity.wrapper.ui.markdown.NativeMarkdownView
import com.antigravity.wrapper.ui.theme.diffColors

@Composable
fun FileDiffViewerSheet(
    data: DiffReviewData,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val collapsedIds = remember { mutableStateMapOf<String, Boolean>() }
    var wrapLines by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val diffPalette = remember(isDark) { diffColors(isDark) }

    FastBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Difference,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = data.title.ifBlank { "Review Changes" },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (data.subtitle.isNotBlank()) {
                            Text(
                                text = data.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { wrapLines = !wrapLines },
                        shape = RoundedCornerShape(999.dp),
                        color = if (wrapLines) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Text(
                            text = "Wrap",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (wrapLines) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !data.isLoading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh diffs", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = requestDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                data.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading file diffs…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                data.files.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.error ?: "No file diffs available for this conversation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = data.files,
                            key = { it.id },
                            contentType = { "diff_file" }
                        ) { file ->
                            val isCollapsed = collapsedIds[file.id] == true
                            DiffFileCard(
                                file = file,
                                diffPalette = diffPalette,
                                isCollapsed = isCollapsed,
                                wrapLines = wrapLines,
                                onToggle = { collapsedIds[file.id] = !isCollapsed }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffFileCard(
    file: DiffFile,
    diffPalette: com.antigravity.wrapper.ui.theme.DiffColors,
    isCollapsed: Boolean,
    wrapLines: Boolean = false,
    onToggle: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceContainerLowest

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (file.path.isNotBlank()) {
                        Text(
                            text = file.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "+${file.addedLines}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = diffPalette.addedAccent
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "-${file.removedLines}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = diffPalette.removedAccent
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = !isCollapsed) {
                if (file.lines.isEmpty()) {
                    Text(
                        text = "Diff lines collapsed or binary file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    val hScroll = rememberScrollState()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            val outerModifier = if (wrapLines) {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(hScroll)
                                    .padding(vertical = 6.dp)
                            }
                            val innerModifier = if (wrapLines) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier
                                    .width(IntrinsicSize.Max)
                                    .widthIn(min = 340.dp)
                            }
                            val lineNumColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                            val hunkBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            val lineNumStyle = remember(lineNumColor) {
                                TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = lineNumColor
                                )
                            }
                            val codeStyle = remember(onSurfaceColor) {
                                TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    letterSpacing = (-0.15).sp,
                                    color = onSurfaceColor
                                )
                            }
                            Column(modifier = outerModifier) {
                                Column(modifier = innerModifier) {
                                    file.lines.forEach { line ->
                                        val bg = when (line.kind) {
                                            DiffLineKind.ADDED -> diffPalette.addedRowBackground
                                            DiffLineKind.REMOVED -> diffPalette.removedRowBackground
                                            DiffLineKind.HUNK -> hunkBgColor
                                            DiffLineKind.CONTEXT -> Color.Transparent
                                        }
                                        val gutterPrefix = when (line.kind) {
                                            DiffLineKind.ADDED -> "+"
                                            DiffLineKind.REMOVED -> "-"
                                            DiffLineKind.HUNK -> " "
                                            DiffLineKind.CONTEXT -> " "
                                        }
                                        val gutterColor = when (line.kind) {
                                            DiffLineKind.ADDED -> diffPalette.addedAccent
                                            DiffLineKind.REMOVED -> diffPalette.removedAccent
                                            else -> lineNumColor
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(bg)
                                                .padding(horizontal = 10.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            if (line.lineNumber.isNotBlank()) {
                                                Text(
                                                    text = line.lineNumber.padStart(3, ' '),
                                                    style = lineNumStyle
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                text = gutterPrefix,
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = gutterColor
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = line.text,
                                                style = codeStyle,
                                                modifier = if (wrapLines) Modifier.weight(1f) else Modifier
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (!wrapLines && hScroll.canScrollBackward) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(14.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(cardBg.copy(alpha = 0.9f), Color.Transparent)
                                            )
                                        )
                                )
                            }
                        }

                        if (!wrapLines && hScroll.canScrollForward) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(24.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, cardBg.copy(alpha = 0.92f))
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentViewerSheet(
    data: DocumentViewerData,
    fontScale: Float,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showRaw by remember { mutableStateOf(false) }

    FastBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = data.title.ifBlank { "Document" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!data.isLoading && data.rawContent.isNotBlank()) {
                        IconButton(onClick = { onCopyText(data.rawContent) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy document", modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = { onShareText(data.rawContent) }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share document", modifier = Modifier.size(19.dp))
                        }
                    }
                    IconButton(onClick = requestDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (!data.isLoading && data.markdownContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(false to "Preview", true to "Raw").forEach { (rawTab, label) ->
                            val selected = showRaw == rawTab
                            Surface(
                                onClick = { showRaw = rawTab },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                data.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading document…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                data.markdownContent.isBlank() && data.rawContent.isBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.error ?: "Document content unavailable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                showRaw -> {
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = data.rawContent,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp * fontScale,
                                lineHeight = 20.sp * fontScale,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        NativeMarkdownView(
                            markdown = data.markdownContent,
                            fontScale = fontScale,
                            onCopyCode = onCopyText,
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceAndActionsSheet(
    conversationTitle: String,
    permissionsPreset: String,
    artifacts: List<WorkspaceArtifactItem>,
    isLoadingArtifacts: Boolean,
    hasSidePanel: Boolean,
    isNewChat: Boolean = false,
    webInspectorEnabled: Boolean = false,
    onToggleWebInspector: (Boolean) -> Unit = {},
    onOpenDiffs: () -> Unit,
    onOpenArtifact: (WorkspaceArtifactItem) -> Unit,
    onOpenPermissions: () -> Unit,
    onRenameThread: (String) -> Unit,
    onPinThread: () -> Unit,
    onArchiveThread: () -> Unit,
    onDismiss: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    val filteredArtifacts = remember(artifacts) {
        artifacts.filterNot { item ->
            val t = item.title.trim()
            t.equals("See less", ignoreCase = true) ||
                t.equals("See more", ignoreCase = true) ||
                t.equals("Show less", ignoreCase = true) ||
                t.equals("Show more", ignoreCase = true) ||
                t.matches(Regex("^Uncommitted(?:\\s*\\(\\d+\\))?$", RegexOption.IGNORE_CASE)) ||
                t.startsWith("Changes not staged", ignoreCase = true) ||
                t.startsWith("Changes to be committed", ignoreCase = true) ||
                t.equals("Untracked files", ignoreCase = true)
        }
    }

    FastBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workspace & Actions",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = requestDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Primary Workspace tools (Diffs & Permissions)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (hasSidePanel || isNewChat) {
                        val diffsEnabled = !isNewChat && hasSidePanel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (diffsEnabled) 1f else 0.38f)
                                .clickable(enabled = diffsEnabled) {
                                    onDismiss()
                                    onOpenDiffs()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Difference,
                                contentDescription = null,
                                tint = if (diffsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Review File Diffs", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    text = if (isNewChat) {
                                        "Start a conversation to review diffs"
                                    } else {
                                        "Inspect code changes in this conversation"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenPermissions()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Remote Permissions", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = permissionsPreset.ifBlank { "Default" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Artifacts & Links from Side Panel
            if (hasSidePanel || isNewChat) {
                Text(
                    text = "ARTIFACTS & DOCUMENTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNewChat) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isNewChat) 0.45f else 1f)
                ) {
                    when {
                        isNewChat -> {
                            Text(
                                text = "Start a conversation to view artifacts and documents.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        isLoadingArtifacts -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Loading workspace artifacts…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        filteredArtifacts.isEmpty() -> {
                            Text(
                                text = "No artifacts listed in this conversation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        else -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                filteredArtifacts.forEachIndexed { idx, item ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onDismiss()
                                                onOpenArtifact(item)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (item.url.isNotBlank()) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (item.subtitle.isNotBlank()) {
                                                Text(
                                                    text = item.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Conversation Actions (Rename, Pin, Archive) — disabled until a conversation starts
            val threadActionsEnabled = !isNewChat
            Text(
                text = "CONVERSATION",
                style = MaterialTheme.typography.labelSmall,
                color = if (threadActionsEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                }
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (threadActionsEnabled) 1f else 0.38f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = threadActionsEnabled) { showRenameDialog = true }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Rename", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = threadActionsEnabled) {
                                onDismiss()
                                onPinThread()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Pin / Unpin", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = threadActionsEnabled) {
                                onDismiss()
                                onArchiveThread()
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val archiveColor = if (threadActionsEnabled) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(Icons.Rounded.Archive, contentDescription = null, tint = archiveColor, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Archive", style = MaterialTheme.typography.bodyMedium, color = archiveColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "SESSION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                onClick = { onToggleWebInspector(!webInspectorEnabled) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Show underlying session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = webInspectorEnabled,
                        onCheckedChange = onToggleWebInspector
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        var newName by remember(conversationTitle) { mutableStateOf(conversationTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        onDismiss()
                        onRenameThread(newName.trim())
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ProjectSelectorSheet(
    projects: List<ProjectOption>,
    activeProject: String,
    onSelectProject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember(projects, activeProject) {
        val base = projects.toMutableList()
        if (activeProject.isNotBlank() && base.none { it.name.equals(activeProject, ignoreCase = true) }) {
            base.add(0, ProjectOption("active", activeProject, true))
        }
        if (base.none { it.name.equals("No Project", ignoreCase = true) }) {
            base.add(ProjectOption("none", "No Project", activeProject.isBlank()))
        }
        base
    }

    FastBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Project", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = requestDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    itemsIndexed(
                        items = options,
                        key = { _, proj -> "${proj.id}:${proj.name}" },
                        contentType = { _, _ -> "project_row" }
                    ) { idx, proj ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                        val isNone = proj.name.equals("No Project", ignoreCase = true)
                        val selected = if (isNone) activeProject.isBlank() else activeProject.equals(proj.name, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectProject(if (isNone) "No Project" else proj.name)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isNone) Icons.Rounded.FolderOff else Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = proj.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsSelectorSheet(
    currentPreset: String,
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(
        Triple("Request Review", "Every command requires approval.", Icons.Rounded.Visibility),
        Triple("Default", "Ask before sensitive operations.", Icons.Rounded.Lock),
        Triple("Turbo", "Run all commands without restrictions.", Icons.Rounded.Shield)
    )

    FastBottomSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { requestDismiss ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Remote Permissions", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = requestDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    presets.forEachIndexed { idx, (name, desc, icon) ->
                        if (idx > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                        val selected = currentPreset.equals(name, ignoreCase = true) ||
                            (currentPreset.isBlank() && name == "Default")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPreset(name)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
