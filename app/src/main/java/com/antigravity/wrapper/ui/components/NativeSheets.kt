package com.antigravity.wrapper.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.wrapper.BuildConfig
import com.antigravity.wrapper.data.AppSettings
import com.antigravity.wrapper.data.BridgeStatus
import com.antigravity.wrapper.data.ModelOption
import com.antigravity.wrapper.data.QuickPrompt
import com.antigravity.wrapper.data.QuotaInfo
import com.antigravity.wrapper.data.QuotaLimitItem
import com.antigravity.wrapper.data.ThemeMode
import kotlin.math.roundToInt

private val MODEL_EFFORT_SUFFIX_REGEX =
    Regex("(?:\\s*\\((?:Minimal|Low|Medium|High|Extra|Fast)\\)|\\s+(?:Minimal|Low|Medium|High|Extra))$", RegexOption.IGNORE_CASE)

private fun normalizeModelLabel(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ")

internal fun matchesActiveModelLabel(modelName: String, activeModelLabel: String): Boolean {
    val target = normalizeModelLabel(modelName)
    val active = normalizeModelLabel(activeModelLabel)
    if (target.isEmpty() || active.isEmpty()) return false
    if (active.equals(target, ignoreCase = true)) return true
    val strippedActive = MODEL_EFFORT_SUFFIX_REGEX.replace(active, "").trim()
    val strippedTarget = MODEL_EFFORT_SUFFIX_REGEX.replace(target, "").trim()
    return strippedActive.equals(target, ignoreCase = true) ||
        (strippedActive.isNotEmpty() && strippedActive.equals(strippedTarget, ignoreCase = true))
}

internal fun resolveSelectedModel(models: List<ModelOption>, activeModelLabel: String): ModelOption? {
    return models.firstOrNull { it.isSelected }
        ?: models.firstOrNull { matchesActiveModelLabel(it.name, activeModelLabel) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    models: List<ModelOption>,
    activeModelLabel: String,
    isRefreshing: Boolean,
    onSelectModel: (modelName: String, effortTier: String) -> Unit,
    onRefreshModels: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasExplicitSelection = remember(models) { models.any { it.isSelected } }
    val activeModelObj = remember(models, activeModelLabel) {
        resolveSelectedModel(models, activeModelLabel)
    }
    val defaultEffortLevels = listOf("Low", "Medium", "High", "Extra")
    val effortLevels = remember(activeModelObj) {
        val opts = activeModelObj?.effortOptions.orEmpty()
        if (opts.any { it.equals("Minimal", ignoreCase = true) }) {
            listOf("Minimal", "Low", "Medium", "High", "Extra")
        } else {
            defaultEffortLevels
        }
    }
    val supportedEfforts = remember(activeModelObj, effortLevels) {
        activeModelObj?.effortOptions?.takeIf { it.isNotEmpty() } ?: effortLevels
    }
    val activeEffort = remember(activeModelLabel, supportedEfforts) {
        val extracted = Regex("\\b(Minimal|Low|Medium|High|Extra|Fast)\\b", RegexOption.IGNORE_CASE)
            .find(activeModelLabel)?.groupValues?.get(1) ?: "High"
        if (supportedEfforts.any { it.equals(extracted, ignoreCase = true) }) {
            extracted
        } else {
            supportedEfforts.lastOrNull() ?: "High"
        }
    }
    var selectedEffort by remember(activeEffort) { mutableStateOf(activeEffort) }
    val showEffortBar = activeModelObj == null || activeModelObj.hasEffortLevels

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .animateContentSize(animationSpec = tween(durationMillis = 200))
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Minimal single-axis header: Model + Refresh + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshModels,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh models",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Single unified Thinking Effort selector (left-padded options + ONE dynamic 4-bar meter on the right)
            if (showEffortBar) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, top = 6.dp, end = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            effortLevels.forEach { effort ->
                                val isSupported = supportedEfforts.any { it.equals(effort, ignoreCase = true) }
                                val isSelectedEffort = isSupported && selectedEffort.equals(effort, ignoreCase = true)
                                val baseModifier = Modifier
                                    .clip(RoundedCornerShape(11.dp))
                                    .alpha(if (isSupported) 1f else 0.34f)
                                    .background(
                                        if (isSelectedEffort) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = (if (isSupported) {
                                        baseModifier.clickable {
                                            selectedEffort = effort
                                            if (activeModelObj != null && activeModelObj.hasEffortLevels) {
                                                onSelectModel(activeModelObj.name, effort)
                                            }
                                        }
                                    } else {
                                        baseModifier
                                    })
                                        .padding(horizontal = 11.dp, vertical = 9.dp)
                                ) {
                                    Text(
                                        text = effort,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isSelectedEffort) FontWeight.SemiBold else FontWeight.Medium
                                        ),
                                        color = if (isSelectedEffort) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        EffortBarsIcon(
                            activeBars = effortLevelCount(selectedEffort),
                            activeColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (models.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = if (isRefreshing) "Loading models…" else "Tap Refresh to sync available models",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
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
                            items = models,
                            key = { _, model -> model.id },
                            contentType = { _, _ -> "model_row" }
                        ) { index, model ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                            val selected = if (hasExplicitSelection) {
                                model.isSelected
                            } else {
                                matchesActiveModelLabel(model.name, activeModelLabel)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        val chosenTier = if (model.hasEffortLevels) {
                                            val validOptions = model.effortOptions.ifEmpty { effortLevels }
                                            if (validOptions.any { it.equals(selectedEffort, ignoreCase = true) }) {
                                                selectedEffort
                                            } else {
                                                validOptions.lastOrNull() ?: "High"
                                            }
                                        } else {
                                            model.tier
                                        }
                                        selectedEffort = chosenTier
                                        onSelectModel(model.name, chosenTier)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                    ),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!model.hasEffortLevels && model.tier.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = model.tier,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Spacer(modifier = Modifier.width(10.dp))
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageQuotaSheet(
    quota: QuotaInfo,
    activeModelLabel: String,
    messageCount: Int,
    bridgeStatus: BridgeStatus,
    isRefreshing: Boolean,
    statusMessage: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    // Auto-load usage after sheet open animation finishes so the slide-up stays jank-free
    LaunchedEffect(Unit) {
        if (!quota.isLiveFromServer && !isRefreshing && bridgeStatus == BridgeStatus.CONNECTED) {
            delay(220L)
            onRefresh()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .animateContentSize(animationSpec = tween(durationMillis = 200))
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Balanced single-axis header (icon-only Refresh + Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Usage & Quota",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh usage",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            val quotaSubtitle = statusMessage?.takeIf { it.isNotBlank() }
                ?: if (quota.isLiveFromServer) "Live usage synced" else null
            if (!quotaSubtitle.isNullOrBlank()) {
                Text(
                    text = quotaSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (quota.limits.isNotEmpty()) {
                val grouped = remember(quota.limits) {
                    quota.limits.groupBy { normalizeQuotaGroupTitle(it.groupName.ifBlank { quota.planLabel }) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    grouped.forEach { (groupName, groupLimits) ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                groupLimits.forEachIndexed { idx, item ->
                                    if (idx > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                    QuotaLimitRow(
                                        item = item,
                                        trackColor = trackColor,
                                        fallbackColor = primaryColor
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val headlinePct = quota.headlineLeftPercent
                val quotaAccessibilitySummary = buildString {
                    append(quota.planLabel)
                    headlinePct?.let { append(", ${it.toInt()} percent left") }
                    if (quota.remainingLabel.isNotBlank()) append(", ${quota.remainingLabel}")
                    if (quota.resetLabel.isNotBlank()) append(", ${quota.resetLabel}")
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = quotaAccessibilitySummary
                        }
                ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.BarChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = quota.planLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when {
                                        isRefreshing -> "Checking live quota…"
                                        quota.remainingLabel.isNotBlank() -> quota.remainingLabel
                                        else -> "Tap Refresh above to sync 5h & weekly limits"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (quota.resetLabel.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = quota.resetLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (headlinePct != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SharpQuotaProgressBar(
                                        leftPercent = headlinePct,
                                        barHeight = 9.dp,
                                        textSize = 13.sp,
                                        trackColor = trackColor,
                                        fillColor = primaryColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val resolvedModel = activeModelLabel.ifBlank { "Syncing…" }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Session info: model $resolvedModel, $messageCount messages, status ${bridgeStatus.label}"
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Model",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = resolvedModel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Messages",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = messageCount.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bridgeStatus.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaLimitRow(
    item: QuotaLimitItem,
    trackColor: Color,
    fallbackColor: Color
) {
    val pct = item.leftPercent
    val ringColor = if (pct != null) quotaRingColor(pct, fallbackColor) else fallbackColor
    val accessibilityLabel = buildString {
        append("${item.groupName} ${item.windowLabel}")
        pct?.let { append(", ${it.toInt()} percent left") }
        if (item.resetLabel.isNotBlank()) append(", ${item.resetLabel}")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.windowLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (item.resetLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.resetLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (pct != null) {
            Spacer(modifier = Modifier.width(12.dp))
            SharpQuotaProgressBar(
                leftPercent = pct,
                barWidth = 88.dp,
                barHeight = 9.dp,
                textSize = 13.sp,
                trackColor = trackColor,
                fillColor = ringColor
            )
        }
    }
}

private fun normalizeQuotaGroupTitle(raw: String): String {
    val cleaned = raw.trim().replace(Regex("\\s+"), " ")
    if (cleaned.isEmpty()) return "Usage Limits"
    return when {
        cleaned.equals("GEMINI MODELS", ignoreCase = true) || cleaned.equals("GEMINI", ignoreCase = true) -> "Gemini Models"
        cleaned.contains("CLAUDE", ignoreCase = true) && cleaned.contains("GPT", ignoreCase = true) -> "Claude & GPT Models"
        cleaned.equals("CLAUDE MODELS", ignoreCase = true) -> "Claude Models"
        else -> cleaned.split(' ').joinToString(" ") { word ->
            when {
                word.equals("gpt", ignoreCase = true) -> "GPT"
                word == "&" || word == "/" -> word
                word.length <= 1 -> word.uppercase()
                else -> word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    }
}

private fun quotaRingColor(leftPercent: Float, fallback: Color): Color = fallback

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label: $value"
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPromptsSheet(
    prompts: List<QuickPrompt>,
    onSelectPrompt: (QuickPrompt) -> Unit,
    onAddPrompt: (title: String, body: String, category: String) -> Unit,
    onDeletePrompt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .animateContentSize(animationSpec = tween(durationMillis = 200))
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgyLogo(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick Prompts",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = prompts,
                    key = { it.id },
                    contentType = { "prompt_row" }
                ) { prompt ->
                    Surface(
                        onClick = {
                            onSelectPrompt(prompt)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prompt.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = prompt.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onDeletePrompt(prompt.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete prompt",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Quick Prompt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Prompt") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddPrompt(title, body, "Custom")
                        showAddDialog = false
                    },
                    enabled = title.isNotBlank() && body.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Full-screen native Settings page (replaces the cramped 95%-height bottom sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsFullScreen(
    settings: AppSettings,
    bridgeStatus: BridgeStatus,
    diagnosticsNote: String,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onFontScaleChange: (Int) -> Unit,
    onAutoExpandReasoningChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onWebInspectorChange: (Boolean) -> Unit,
    onCopyDomSnapshot: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    var sliderScale by remember(settings.fontScalePercent) {
        mutableFloatStateOf(settings.fontScalePercent.toFloat())
    }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    var themeDropdownExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = themeDropdownExpanded,
                        onExpandedChange = { themeDropdownExpanded = !themeDropdownExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = settings.themeMode.label,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Theme") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(settings.themeMode.swatchArgb))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                            shape = CircleShape
                                        )
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = themeDropdownExpanded,
                            onDismissRequest = { themeDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 360.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val selected = mode == settings.themeMode
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(Color(mode.swatchArgb))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                                    shape = CircleShape
                                                )
                                        )
                                    },
                                    trailingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        onThemeModeChange(mode)
                                        themeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.themeMode == ThemeMode.SYSTEM) {
                        Spacer(modifier = Modifier.height(12.dp))
                        SheetSwitchRow(
                            icon = Icons.Rounded.Palette,
                            title = "Material You dynamic colors",
                            checked = settings.dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Message text size",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val scalePresets = listOf(90 to "Small", 100 to "Default", 115 to "Large", 130 to "XL")
                    val closestPreset = scalePresets.minByOrNull { kotlin.math.abs(it.first - settings.fontScalePercent) }?.first ?: 100
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        scalePresets.forEachIndexed { idx, (pct, label) ->
                            SegmentedButton(
                                selected = closestPreset == pct,
                                onClick = { onFontScaleChange(pct) },
                                shape = SegmentedButtonDefaults.itemShape(idx, scalePresets.size),
                                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            // Reading & Behavior
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Experience",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SheetSwitchRow(
                        icon = Icons.Rounded.Psychology,
                        title = "Auto-expand reasoning blocks",
                        checked = settings.autoExpandReasoning,
                        onCheckedChange = onAutoExpandReasoningChange
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    SheetSwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = "Haptic feedback",
                        checked = settings.hapticFeedbackEnabled,
                        onCheckedChange = onHapticFeedbackChange
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    SheetSwitchRow(
                        icon = Icons.Rounded.ScreenLockPortrait,
                        title = "Keep screen awake while generating",
                        checked = settings.keepScreenAwake,
                        onCheckedChange = onKeepScreenAwakeChange
                    )
                }
            }

            // Collapsible Developer Diagnostics (raw telemetry hidden cleanly by default)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showDiagnostics = !showDiagnostics }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Developer Diagnostics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showDiagnostics) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showDiagnostics) {
                        Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                            Text(
                                text = "Bridge: ${bridgeStatus.label}" +
                                    if (diagnosticsNote.isNotBlank()) " • $diagnosticsNote" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FilledTonalButton(
                                onClick = onCopyDomSnapshot,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Sanitized DOM Snapshot")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SheetSwitchRow(
                                icon = Icons.Rounded.Visibility,
                                title = "Show underlying session",
                                checked = settings.webInspectorMode,
                                onCheckedChange = {
                                    onWebInspectorChange(it)
                                    if (it) onBack()
                                }
                            )
                        }
                    }
                }
            }

            // Account & Version
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showSignOutConfirm = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sign out & clear Google session",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Antigravity Android v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign out of Antigravity?") },
            text = { Text("This clears session cookies and returns to Google Sign-In.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        onBack()
                        onSignOut()
                    }
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SheetSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun EmptyChatWelcome(
    bridgeStatus: BridgeStatus,
    activeProjectLabel: String,
    activeModelLabel: String,
    isSwitchingConversation: Boolean,
    errorMessage: String?,
    onOpenProjectSelector: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onRetryConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSwitchingConversation) {
                SwitchingConversationLoader()
                return@Box
            }

            if (bridgeStatus == BridgeStatus.OFFLINE || bridgeStatus == BridgeStatus.ERROR) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (bridgeStatus == BridgeStatus.OFFLINE) "You're offline" else "Couldn't reach Antigravity",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onRetryConnect) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                        FilledTonalButton(onClick = onOpenSettings) {
                            Text("Settings")
                        }
                    }
                }
                return@Box
            }

            val projectShort = remember(activeProjectLabel) {
                activeProjectLabel.trim()
                    .substringAfterLast('/')
                    .ifBlank { activeProjectLabel.trim() }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (projectShort.isNotBlank()) {
                    Text(
                        text = buildAnnotatedString {
                            append("What should we build\nin ")
                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                append(projectShort)
                            }
                            append("?")
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenProjectSelector)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = if (bridgeStatus == BridgeStatus.CONNECTING) {
                            "Connecting to Antigravity…"
                        } else {
                            "What should we build\ntoday?"
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LaptopMac,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "on Antigravity",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (bridgeStatus == BridgeStatus.CONNECTED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchingConversationLoader() {
    val progress = remember { Animatable(0.06f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0.55f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
        progress.animateTo(
            targetValue = 0.84f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
        progress.animateTo(
            targetValue = 0.94f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "SwitchLoaderShimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SwitchShimmerSweep"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = MaterialTheme.colorScheme.primary
    val shimmerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.28f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .widthIn(min = 180.dp, max = 260.dp)
                .height(10.dp)
        ) {
            drawRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = size
            )
            val fillWidth = (size.width * progress.value).coerceIn(0f, size.width)
            if (fillWidth > 0f) {
                drawRect(
                    color = fillColor,
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, size.height)
                )
                val bandWidth = (fillWidth * 0.42f).coerceAtLeast(24f)
                val bandStart = (shimmerOffset * fillWidth) - (bandWidth * 0.5f)
                val clippedStart = bandStart.coerceIn(0f, fillWidth)
                val clippedEnd = (bandStart + bandWidth).coerceIn(0f, fillWidth)
                val clippedW = clippedEnd - clippedStart
                if (clippedW > 1f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, shimmerColor, Color.Transparent),
                            startX = bandStart,
                            endX = bandStart + bandWidth
                        ),
                        topLeft = Offset(clippedStart, 0f),
                        size = Size(clippedW, size.height)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Loading conversation…",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
