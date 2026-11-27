package com.antigravity.wrapper.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs

private val FAST_SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

private val SHEET_OPEN_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 2200f
)

private val SHEET_DISMISS_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 2600f
)

private val SHEET_SNAP_BACK_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 2000f
)

/**
 * Low-latency modal bottom sheet rendered inline inside the root Compose overlay host:
 * - Zero `Dialog` window startup delay on open.
 * - Direct 1:1 finger tracking via `Animatable.snapTo` with `CoroutineStart.UNDISPATCHED`.
 * - Draw-phase-only `graphicsLayer { translationY }` and `drawBehind` scrim fade (zero layout
 *   passes or content recompositions while dragging).
 * - Immediate handle capture on the first pixels + seamless nested-scroll handoff at scroll offset 0.
 * - Critically damped, velocity-preserving dismiss spring (`Spring.DampingRatioNoBouncy`).
 */
@Composable
fun FastBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.(requestDismiss: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val initialOffscreenPx = with(density) { 900.dp.toPx() }
    val dismissDistancePx = with(density) { 116.dp.toPx() }
    val offsetY = remember { Animatable(initialOffscreenPx) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    var openedOnce by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var isDirectDragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val isScrimInteractive by remember {
        derivedStateOf {
            !isDismissing && (sheetHeightPx <= 0f || offsetY.value < sheetHeightPx - 1f)
        }
    }

    fun snapToOffset(next: Float) {
        if (isDismissing) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetY.snapTo(next.coerceAtLeast(0f))
        }
    }

    fun dismissWithMomentum(velocityY: Float = 0f) {
        if (isDismissing) return
        isDismissing = true
        val target = (if (sheetHeightPx > 0f) sheetHeightPx else initialOffscreenPx).coerceAtLeast(1f)
        scope.launch {
            offsetY.animateTo(
                targetValue = target,
                animationSpec = SHEET_DISMISS_SPRING,
                initialVelocity = velocityY.coerceAtLeast(0f)
            )
            currentOnDismiss()
        }
    }

    fun settleAfterDrag(velocityY: Float) {
        if (isDismissing) return
        val h = if (sheetHeightPx > 0f) sheetHeightPx else initialOffscreenPx
        val dismissThreshold = minOf(h * 0.30f, dismissDistancePx)
        val shouldDismiss = velocityY > 550f || (velocityY >= -250f && offsetY.value > dismissThreshold)
        if (shouldDismiss) {
            dismissWithMomentum(velocityY)
        } else {
            scope.launch {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = SHEET_SNAP_BACK_SPRING,
                    initialVelocity = velocityY
                )
            }
        }
    }

    val requestDismiss = remember {
        { dismissWithMomentum(0f) }
    }

    BackHandler(enabled = !isDismissing) {
        dismissWithMomentum(0f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && offsetY.value > 0f && !isDismissing) {
                    val next = (offsetY.value + available.y).coerceAtLeast(0f)
                    val consumedY = next - offsetY.value
                    snapToOffset(next)
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !isDismissing) {
                    snapToOffset(offsetY.value + available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 0f && !isDismissing) {
                    settleAfterDrag(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offsetY.value > 0f && !isDismissing && !offsetY.isRunning) {
                    settleAfterDrag(available.y)
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Draw-phase backdrop scrim fade (zero recompositions during drag)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val h = if (sheetHeightPx > 0f) sheetHeightPx else size.height.coerceAtLeast(1f)
                    val progress = (1f - (offsetY.value / h)).coerceIn(0f, 1f)
                    if (progress > 0.001f) {
                        drawRect(Color.Black.copy(alpha = 0.6f * progress))
                    }
                }
                .then(
                    if (isScrimInteractive) {
                        Modifier.pointerInput(requestDismiss) {
                            detectTapGestures { requestDismiss() }
                        }
                    } else {
                        Modifier
                    }
                )
        )

        Surface(
            shape = FAST_SHEET_SHAPE,
            color = containerColor,
            tonalElevation = 0.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .onSizeChanged { measured ->
                    val h = measured.height.toFloat().coerceAtLeast(1f)
                    sheetHeightPx = h
                    if (!openedOnce) {
                        openedOnce = true
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            offsetY.snapTo(h)
                            offsetY.animateTo(0f, SHEET_OPEN_SPRING)
                        }
                    }
                }
                .graphicsLayer {
                    translationY = offsetY.value
                }
                .nestedScroll(nestedScrollConnection)
                // Direct vertical drag capture on header / non-scrollable regions
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (isDismissing) return@awaitEachGesture
                        var captured = false
                        val dragStart = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            val shouldCapture = overSlop > 0f || offsetY.value > 0f
                            if (shouldCapture) {
                                change.consume()
                                captured = true
                                isDirectDragging = true
                                tracker.resetTracking()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                tracker.addPosition(change.uptimeMillis, change.position)
                                snapToOffset(offsetY.value + overSlop)
                            }
                        }
                        if (!captured || dragStart == null) return@awaitEachGesture

                        try {
                            verticalDrag(dragStart.id) { change ->
                                val dy = change.positionChange().y
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                snapToOffset(offsetY.value + dy)
                            }
                            settleAfterDrag(tracker.calculateVelocity().y)
                        } finally {
                            isDirectDragging = false
                        }
                    }
                }
                // Safety settle if a nested-scroll drag released without triggering fling
                .pointerInput(Unit) {
                    awaitEachGesture {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                        if (offsetY.value > 0f && !isDirectDragging && !offsetY.isRunning && !isDismissing) {
                            settleAfterDrag(0f)
                        }
                    }
                }
                // Prevent taps on sheet background from leaking through to the scrim
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Dedicated drag handle: zero slop dead-zone, moves on first 1-2px downward motion
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            val tracker = VelocityTracker()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (isDismissing) return@awaitEachGesture
                                tracker.resetTracking()
                                tracker.addPosition(down.uptimeMillis, down.position)
                                var started = false
                                while (!started) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val totalDx = abs(change.position.x - down.position.x)
                                    val totalDy = change.position.y - down.position.y
                                    if (totalDy > 1.5f && totalDy >= totalDx) {
                                        change.consume()
                                        started = true
                                        isDirectDragging = true
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        snapToOffset(offsetY.value + totalDy)
                                    } else if (totalDx > 16f && totalDx > abs(totalDy)) {
                                        break
                                    }
                                }
                                if (!started) return@awaitEachGesture
                                try {
                                    verticalDrag(down.id) { change ->
                                        val dy = change.positionChange().y
                                        change.consume()
                                        tracker.addPosition(change.uptimeMillis, change.position)
                                        snapToOffset(offsetY.value + dy)
                                    }
                                    settleAfterDrag(tracker.calculateVelocity().y)
                                } finally {
                                    isDirectDragging = false
                                }
                            }
                        }
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                    )
                }

                content(requestDismiss)
            }
        }
    }
}
