/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.haptics.samples.ui.resist

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.android.haptics.samples.R
import com.example.android.haptics.samples.ui.components.Screen
import com.example.android.haptics.samples.ui.theme.HapticSamplerTheme
import com.example.android.haptics.samples.ui.utils.lerp
import kotlinx.coroutines.delay

// Vibration related constants for the resist effect.
private const val TICK_INTERVAL_MIN_MS = 25L
private const val TICK_INTERVAL_MAX_MS = 60L
private const val TICK_INTENSITY_MIN = 0.2f
private const val TICK_INTENSITY_MAX = 0.8f

// Start and target values for the resistance indicator on screen.
private val START_SIZE = 64.dp
private val START_STROKE_WIDTH = 8.dp
private const val START_INITIAL_ROTATION = 90f
private val START_Y_OFFSET = 0.dp

private val TARGET_SIZE = 128.dp
private val TARGET_DRAG_OFFSET = 350.dp
private val TARGET_Y_OFFSET = 150.dp
private val TARGET_STROKE_SIZE = 16.dp
private const val TARGET_ROTATION = START_INITIAL_ROTATION + 360f
private const val TIME_TO_ANIMATE_BACK_MS = 1000

// The buffer we use before changing indicators (otherwise small unintended finger movement causes flickering).
private val DRAG_OFFSET_BUFFER = 5.dp

@Composable
fun ResistRoute(viewModel: ResistViewModel) {
    ResistScreen(
        isLowTickSupported = viewModel.isLowTickSupported,
        messageToUser = viewModel.messageToUser,
    )
}

@Composable
fun ResistScreen(isLowTickSupported: Boolean, messageToUser: String = "") {
    // Use density of user's device to determine the maxDragOffset (amount of drag we consider animation complete)
    // and the dragOffsetBuffer used so there is no flicker from unintended finger movement.
    val maxDragOffset = with(LocalDensity.current) { TARGET_DRAG_OFFSET.toPx() }
    val dragOffsetBuffer = with(LocalDensity.current) { DRAG_OFFSET_BUFFER.toPx() }

    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val vibrator = LocalContext.current.getSystemService(Vibrator::class.java)

    isDragging = dragOffset > 0

    // State of the animated refresh indicator.
    var size by remember { mutableStateOf(START_SIZE) }
    var strokeWidth by remember { mutableStateOf(START_SIZE) }
    var rotation by remember { mutableStateOf(START_INITIAL_ROTATION) }
    var offsetY by remember { mutableStateOf(START_Y_OFFSET) }

    // The interval and the intensity of the vibrations.
    var vibrationInterval by remember { mutableStateOf(TICK_INTERVAL_MAX_MS) }
    var vibrationIntensity by remember { mutableStateOf(TICK_INTENSITY_MIN) }

    // Use an interpolator to simulate resistance as the user approaches end of their drag.
    val interpolator = DecelerateInterpolator()

    // Use the drag distance to calculate the indicator state and the vibration intensity.
    dragOffset = dragOffset.coerceIn(0f, maxDragOffset)
    when (dragOffset) {
        0f -> {
            // Initial state of the indicator.
            offsetY = START_Y_OFFSET
            size = START_SIZE
            strokeWidth = START_STROKE_WIDTH
            rotation = START_INITIAL_ROTATION

            vibrationInterval = TICK_INTERVAL_MAX_MS
            vibrationIntensity = TICK_INTENSITY_MIN
        }
        maxDragOffset -> {
            offsetY = TARGET_Y_OFFSET
            size = TARGET_SIZE
            strokeWidth = TARGET_STROKE_SIZE
            rotation = TARGET_ROTATION

            vibrationInterval = TICK_INTERVAL_MIN_MS
            vibrationIntensity = TICK_INTENSITY_MAX
        }
        else -> {
            // User is dragging between start and max drag distance.
            val relativeDragOffset = interpolator.getInterpolation(dragOffset / maxDragOffset)

            // Use the relativeDragOffset to interpolate between start and target values.
            offsetY = Dp(lerp(0f, TARGET_Y_OFFSET.value, relativeDragOffset))
            size = Dp(lerp(START_SIZE.value, TARGET_SIZE.value, relativeDragOffset))
            strokeWidth = Dp(lerp(START_STROKE_WIDTH.value, TARGET_STROKE_SIZE.value, relativeDragOffset))
            rotation = lerp(START_INITIAL_ROTATION, TARGET_ROTATION, relativeDragOffset)
            // We want the interval to decrease (more frequent vibrations) as user drags down to simulate resistance.
            vibrationInterval = lerp(TICK_INTERVAL_MIN_MS.toFloat(), TICK_INTERVAL_MAX_MS.toFloat(), (1f - relativeDragOffset)).toLong()
            vibrationIntensity = lerp(TICK_INTENSITY_MIN, TICK_INTENSITY_MAX, relativeDragOffset)
        }
    }

    if (isDragging) {
        LaunchedEffect(Unit) {
            // We must continuously run this effect because we want vibration to occur even when the
            // view is not being drawn, which is the case if user stops dragging midway through
            // animation.
            while (true) {
                delay(vibrationInterval)
                vibrateTick(
                    vibrator = vibrator, isLowTickSupported = isLowTickSupported,
                    intensity = vibrationIntensity
                )
            }
        }
    }


    val transitionData = updateResistIndicatorTransitionData(isDragging, size, strokeWidth, rotation, offsetY)
    Screen(pageTitle = stringResource(R.string.resist_screen_title), messageToUser = messageToUser) {
        Column(
            Modifier
                .draggable(
                    orientation = Orientation.Vertical,
                    onDragStopped = {
                        dragOffset = 0f
                    },
                    state = rememberDraggableState { delta ->
                        dragOffset += delta
                    }
                )
                .fillMaxWidth()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ResistIndicator(
                size = transitionData.size, offsetY = transitionData.offsetY, strokeWidth = transitionData.stroke,
                rotation = transitionData.rotation, dragOffset = dragOffset, maxDragOffset = maxDragOffset,
                dragOffsetBuffer = dragOffsetBuffer
            )
        }
    }
}

@Composable
private fun ResistIndicator(size: Dp, offsetY: Dp, strokeWidth: Dp, rotation: Float, dragOffset: Float, maxDragOffset: Float, dragOffsetBuffer: Float) {
    Box() {
        val isAtStart = dragOffset == 0f || dragOffset < dragOffsetBuffer
        val isAtTarget = dragOffset >= maxDragOffset - dragOffsetBuffer

        Column(modifier = Modifier.align(Alignment.Center)) {
            CircularProgressIndicator(
                progress = 0.75f,
                modifier = Modifier
                    .padding(8.dp)
                    .size(size)
                    .offset(y = offsetY)
                    .rotate(rotation),
                color = MaterialTheme.colors.primaryVariant,
                strokeWidth = strokeWidth
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = isAtStart,
                enter = fadeIn(animationSpec = tween(delayMillis = TIME_TO_ANIMATE_BACK_MS)),
                exit = fadeOut(),

            ) {
                Text(stringResource(R.string.resist_screen_drag_down), Modifier.offset(y = START_SIZE / 2))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = offsetY)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isAtTarget,
                enter = fadeIn(),
                exit = fadeOut()
            ) {

                // Indicator that max resistance has been reached.
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.secondary)
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.Center)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isAtStart,
                enter = fadeIn(animationSpec = tween(delayMillis = TIME_TO_ANIMATE_BACK_MS)),
                exit = fadeOut()
            ) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    null,
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier
                        .offset(y = -(4.dp))
                        .align(Alignment.Center)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 16.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isAtTarget,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(stringResource(R.string.resist_screen_and_release))
            }
        }
    }
}

/**
 * Animated values for the ResistIndicator (we only need to animate back to initial position).
 */
private data class ResistIndicatorTransitionData(val size: Dp, val stroke: Dp, val rotation: Float, val offsetY: Dp)

@Composable
private fun updateResistIndicatorTransitionData(
    isDragging: Boolean,
    size: Dp,
    strokeWidth: Dp,
    rotation: Float,
    offsetY: Dp
): ResistIndicatorTransitionData {
    // TODO(jmylen): Remove this variable and code block. Using updateTransition does not seem to
    // consistently be working. Likely a bug in my code but not clear what the problem is.
    val useSeparateAnimationSpecs = true

    if (useSeparateAnimationSpecs) {
        val sizeAnimated by animateDpAsState(
            targetValue = if (isDragging) size else START_SIZE,
            animationSpec = getAnimationSpec(isDragging)
        )
        val strokeWidthAnimated by animateDpAsState(
            targetValue = if (isDragging) strokeWidth else START_STROKE_WIDTH,
            animationSpec = getAnimationSpec(isDragging)
        )
        val rotationAnimated by animateFloatAsState(
            targetValue = if (isDragging) rotation else START_INITIAL_ROTATION,
            animationSpec = getAnimationSpec(isDragging)
        )
        val offsetYAnimated by animateDpAsState(
            targetValue = if (isDragging) offsetY else START_Y_OFFSET,
            animationSpec = getAnimationSpec(isDragging)
        )
        return ResistIndicatorTransitionData(
            size = sizeAnimated, stroke = strokeWidthAnimated,
            rotation = rotationAnimated, offsetY = offsetYAnimated
        )
    } else {
        val transition = updateTransition(isDragging, label = "Animate resist indicator.")
        val sizeAnimated by transition.animateDp(
            label = "Animate the size of the resist indicator.",
            transitionSpec = getTransitionSpec()
        ) { state: Boolean ->
            when (state) {
                true -> size
                else -> START_SIZE
            }
        }
        val strokeWidthAnimated by transition.animateDp(
            label = "Animate the stroke width of the resist indicator.",
            transitionSpec = getTransitionSpec()
        ) { state: Boolean ->
            when (state) {
                true -> strokeWidth
                else -> START_STROKE_WIDTH
            }
        }

        val rotationAnimated by transition.animateFloat(
            label = "Animate the rotation of the resist indicator.",
            transitionSpec = getTransitionSpec()
        ) { state: Boolean ->
            when (state) {
                true -> rotation
                else -> START_INITIAL_ROTATION
            }
        }

        val offsetYAnimated by transition.animateDp(
            label = "Animate the rotation of the resist indicator.",
            transitionSpec = getTransitionSpec()
        ) { state: Boolean ->
            when (state) {
                true -> offsetY
                else -> START_Y_OFFSET
            }
        }
        return ResistIndicatorTransitionData(
            size = sizeAnimated, stroke = strokeWidthAnimated,
            rotation = rotationAnimated, offsetY = offsetYAnimated
        )
    }
}

@Composable
private fun <T> getAnimationSpec(isDragging: Boolean): AnimationSpec<T> {
    return if (isDragging) tween(0) else tween(TIME_TO_ANIMATE_BACK_MS)
}

@Composable
private fun <T> getTransitionSpec(): @Composable (Transition.Segment<Boolean>.() -> FiniteAnimationSpec<T>) =
    {
        when {
            // Animate back once user stops dragging.
            true isTransitioningTo false -> {
                tween(TIME_TO_ANIMATE_BACK_MS)
            }
            // Otherwise no transition.
            else -> {
                tween(0)
            }
        }
    }

private fun vibrateTick(vibrator: Vibrator, isLowTickSupported: Boolean, intensity: Float) {
    // Composition primitives require Android R.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

    // By default the preferred primitive for this experience is low tick instead of tick.
    var vibrationEffectToUse = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
    var vibrationIntensityToUse = intensity
    if (!isLowTickSupported) {
        // Use tick and cut intensity if low tick is not supported.
        vibrationEffectToUse = VibrationEffect.Composition.PRIMITIVE_TICK
        vibrationIntensityToUse *= .75f
    }

    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                vibrationEffectToUse,
                vibrationIntensityToUse,
            )
            .compose()
    )
}

@Preview(showBackground = true)
@Composable
fun ResistScreenPreview() {
    HapticSamplerTheme {
        ResistScreen(
            isLowTickSupported = false,
            messageToUser = "A message to display to user."
        )
    }
}
