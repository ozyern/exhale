/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.ozyern.exhale.R

/**
 * The "a new version is out" sheet that greets you on launch.
 *
 * The app's mark, large and lit by a soft glow of the accent behind it, settles in as the sheet
 * rises; under it a quiet "New update" pill, the new version as the headline and where you are
 * going from as one grey line with an arrow. What's new sits in a grouped card of its own that
 * scrolls inside itself, so the two buttons never walk off the bottom: a filled capsule to update,
 * and "Not now" in the accent under it. The footnote says what the button does — the app fetches
 * the build itself, and you can keep listening.
 */
@Composable
fun ColumnScope.NewVersionSheet(
    latestVersion: String,
    currentVersion: String,
    notes: String?,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    // The mark settles in with the sheet: a little under size and transparent, springing home.
    val arrive = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrive.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f)) }
    // The words follow a beat behind it.
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) { words.animateTo(1f, tween(420, delayMillis = 120, easing = LinearOutSlowInEasing)) }

    Spacer(Modifier.height(8.dp))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .drawBehind {
                // The glow behind the mark, in the accent, so the moment feels like news.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.38f * arrive.value), Color.Transparent),
                        center = center,
                        radius = size.height * 0.75f,
                    ),
                    radius = size.height * 0.75f,
                )
            },
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    val s = 0.82f + 0.18f * arrive.value
                    scaleX = s
                    scaleY = s
                    alpha = arrive.value.coerceIn(0f, 1f)
                }
                .shadow(18.dp, RoundedCornerShape(22.dp), ambientColor = accent.copy(alpha = 0.5f), spotColor = accent.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(22.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = words.value
                translationY = (1f - words.value) * 12.dp.toPx()
            },
    ) {
        Text(
            text = "New update",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = accent,
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name) + " " + latestVersion,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.5).sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = currentVersion, fontSize = 15.sp, color = quiet)
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = quiet,
                modifier = Modifier.padding(horizontal = 6.dp).size(15.dp),
            )
            Text(text = latestVersion, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink)
        }
    }

    Spacer(Modifier.height(20.dp))

    // What's new: a grouped card that scrolls inside itself. fill = false so a two-line changelog
    // makes a short sheet rather than stretching one paragraph down the screen.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .clip(RoundedCornerShape(20.dp))
            .background(ink.copy(alpha = 0.06f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.update_whats_new),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (!notes.isNullOrBlank()) {
                Markdown(content = notes, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    text = stringResource(R.string.release_notes_unavailable),
                    fontSize = 15.sp,
                    color = quiet,
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onUpdate),
        ) {
            Icon(
                painter = painterResource(R.drawable.download),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.update_now),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.height(4.dp))
        // The accent, not grey: a greyed-out alternative reads as disabled. Weight and position
        // carry the hierarchy.
        Text(
            text = stringResource(R.string.update_not_now),
            fontSize = 17.sp,
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onLater)
                .padding(vertical = 12.dp),
        )
        Text(
            text = stringResource(R.string.update_background_footnote),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = quiet.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
