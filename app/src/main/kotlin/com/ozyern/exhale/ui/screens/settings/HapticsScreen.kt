/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.EnableHapticFeedbackKey
import com.ozyern.exhale.constants.HapticFeelKey
import com.ozyern.exhale.constants.HapticIntensityKey
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.component.liquid.LiquidSlider
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.ExhaleHaptics
import com.ozyern.exhale.utils.HapticFeel
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Haptics, laid out the way the phone lays out its own: a card that shows what the feel is — press
 * play and it plays, on the motor and on the screen at once — then the switch, how strong, and how
 * a touch feels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (enabled, setEnabled) = rememberPreference(EnableHapticFeedbackKey, true)
    val (intensity, setIntensity) = rememberPreference(HapticIntensityKey, 0.75f)
    val (feel, setFeel) = rememberEnumPreference(HapticFeelKey, HapticFeel.CRISP)
    var sliderValue by remember(intensity) { mutableFloatStateOf(intensity) }
    var choosingFeel by remember { mutableStateOf(false) }
    // The preview's beat: each ripple ring rises with the vibration it shows.
    val pulse = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }

    fun preview() {
        if (playing) return
        playing = true
        scope.launch {
            ExhaleHaptics.demo(context, feel, sliderValue) {
                scope.launch {
                    pulse.snapTo(0f)
                    pulse.animateTo(1f, tween(420))
                }
            }
            playing = false
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            // Below the content, not around the viewport: the page scrolls on under the dock.
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        // ---- The card: what it feels like, shown and played ----
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .clickable(enabled = enabled) { preview() },
            ) {
                HapticsIllustration(feel = feel, pulse = { pulse.value }, modifier = Modifier.fillMaxSize())
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(58.dp)
                        .graphicsLayer { alpha = if (playing) 0f else 1f }
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.32f)),
                ) {
                    Icon(
                        painterResource(R.drawable.play),
                        contentDescription = "Play a preview",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    "Exhale Haptics",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every tap, drag and scrub speaks through the motor — crisp like a key, soft like cloth, " +
                        "or rich with a little weight. Tune how strong it is and how a touch feels.",
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PreferenceGroup {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setEnabled(!enabled) }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Haptic feedback", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Taps, drags, the scrubber and the player. Off silences every vibration in Exhale.",
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                LiquidToggle(checked = enabled, onCheckedChange = setEnabled)
            }

            val live = enabled && feel != HapticFeel.SYSTEM
            Column(
                Modifier
                    .graphicsLayer { alpha = if (live) 1f else 0.4f }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text("Haptic intensity", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                LiquidSlider(
                    value = sliderValue,
                    onValueChange = { if (live) sliderValue = it },
                    onValueChangeFinished = {
                        if (live) {
                            setIntensity(sliderValue)
                            // Felt where it was set: one click at the new strength.
                            ExhaleHaptics.play(context, strong = true, feel = feel, intensity = sliderValue)
                        }
                    },
                    valueRange = 0.1f..1f,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                    .clickable(enabled = enabled) { choosingFeel = !choosingFeel }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    "How touches feel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(feel.label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Icon(
                    painterResource(R.drawable.chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = if (choosingFeel) 90f else 0f },
                )
            }
            if (choosingFeel) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    HapticFeel.entries.forEach { option ->
                        val chosen = option == feel
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                )
                                .clickable {
                                    setFeel(option)
                                    // Each choice is felt as it is made.
                                    ExhaleHaptics.play(context, strong = true, feel = option, intensity = sliderValue)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(option.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text(option.detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (chosen) {
                                Icon(
                                    painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    SettingsTopAppBar(
        title = { Text("Haptics") },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}

/**
 * The card's picture: a phone lying in a soft blue light, with the vibration drawn as rings
 * rising off it — a slow idle breath, and a strong ripple on every beat of the preview. The rings'
 * shape follows the feel: tight for Crisp, wide and faint for Soft, heavy for Rich.
 */
@Composable
private fun HapticsIllustration(feel: HapticFeel, pulse: () -> Float, modifier: Modifier = Modifier) {
    val idle = rememberInfiniteTransition(label = "hapticsIdle")
    val breath by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = LinearEasing), RepeatMode.Restart),
        label = "hapticsBreath",
    )
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFD6E4FB), Color(0xFFB3CBF4), Color(0xFF8FB0EC))))
        val center = Offset(size.width / 2f, size.height * 0.55f)
        val (spread, weight) = when (feel) {
            HapticFeel.CRISP, HapticFeel.SYSTEM -> 0.9f to 2.2f
            HapticFeel.SOFT -> 1.25f to 1.4f
            HapticFeel.RICH -> 1.05f to 3.4f
        }
        // Idle rings: three, staggered, forever rising and fading.
        for (i in 0 until 3) {
            val t = (breath + i / 3f) % 1f
            val r = size.minDimension * (0.22f + 0.5f * spread * t)
            drawCircle(
                color = Color.White.copy(alpha = 0.55f * (1f - t)),
                radius = r,
                center = center,
                style = Stroke(width = weight.dp.toPx()),
            )
        }
        // The beat: one strong ring off each vibration of the preview.
        val p = pulse()
        if (p in 0.001f..0.999f) {
            val r = size.minDimension * (0.2f + 0.7f * spread * p)
            drawCircle(
                color = Color(0xFF3F6FE0).copy(alpha = 0.7f * (1f - p)),
                radius = r,
                center = center,
                style = Stroke(width = (weight * 2.4f).dp.toPx()),
            )
        }
        // The phone, lying flat, shivering a hair on the beat.
        val shiver = if (p in 0.001f..0.5f) sin(p * PI.toFloat() * 16f) * 2.dp.toPx() else 0f
        val w = size.minDimension * 0.34f
        val h = w * 1.9f * 0.5f
        val topLeft = Offset(center.x - w / 2f + shiver, center.y - h / 2f)
        drawRoundRect(
            color = Color(0xFF5A83DE),
            topLeft = topLeft.copy(y = topLeft.y + 6.dp.toPx()),
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.16f),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFFEAF1FF), Color(0xFFC6D8FA)), startY = topLeft.y, endY = topLeft.y + h),
            topLeft = topLeft,
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.16f),
        )
        drawRoundRect(
            color = Color(0xFF7E9EE6).copy(alpha = 0.5f),
            topLeft = Offset(topLeft.x + w * 0.36f, topLeft.y + h * 0.08f),
            size = Size(w * 0.28f, h * 0.06f),
            cornerRadius = CornerRadius(h * 0.03f),
        )
    }
}
