/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R

/**
 * iOS Settings' search: a frosted capsule floating at the bottom of the page — magnifier, "Search",
 * microphone — that rides up with the keyboard and narrows the list above it as you type.
 *
 * Frosted rather than live glass: this sits inside the NavHost, and in-content glass must not sample
 * the app's backdrop recording (see rememberInContentBackdrop), so it is a dense translucent fill
 * with a light rim and a soft shadow instead.
 */
@Composable
internal fun SettingsBottomSearch(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val focusManager = LocalFocusManager.current
    val ink = MaterialTheme.colorScheme.onSurface
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) onQueryChange(TextFieldValue(spoken, TextRange(spoken.length)))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            // Above the mini player when one is showing, and above the keyboard while typing.
            .windowInsetsPadding(
                com.ozyern.exhale.LocalPlayerAwareWindowInsets.current
                    .only(androidx.compose.foundation.layout.WindowInsetsSides.Bottom)
                    .union(androidx.compose.foundation.layout.WindowInsets.ime),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .shadow(18.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
                .clip(CircleShape)
                .background(if (dark) Color(0xEB2A2A2E) else Color(0xF2F4F4F7))
                .border(
                    0.8.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.8f), Color.White.copy(alpha = 0.04f))),
                    CircleShape,
                )
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.search), null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.text.isEmpty()) {
                    Text(stringResource(R.string.search), fontSize = 17.sp, color = ink.copy(alpha = 0.45f))
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 17.sp, color = ink),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (query.text.isNotEmpty()) {
                            onClear()
                        } else {
                            runCatching {
                                voice.launch(
                                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM),
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (query.text.isNotEmpty()) R.drawable.close else R.drawable.mic),
                    contentDescription = null,
                    tint = ink.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (query.text.isNotEmpty()) 18.dp else 22.dp),
                )
            }
        }
    }
}
