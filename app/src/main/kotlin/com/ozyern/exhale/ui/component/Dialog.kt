/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.ozyern.exhale.ui.screens.settings.AccountSettings
import com.ozyern.exhale.R

/**
 * Shared translucent "liquid glass" surface used by every dialog in the app.
 *
 * Compose renders dialogs in a separate window, so a real behind-content backdrop blur
 * (kyant [com.kyant.backdrop] / Haze) cannot sample the app content the way the in-window
 * top bar / nav bar do. Instead we lean on a highly translucent layered surface + a bright
 * inner highlight border over the system scrim, which reads as frosted glass and — crucially —
 * never triggers the re-entrant GraphicsLayer draw that crashes in-content backdrop consumers.
 */
@Composable
private fun GlassDialogSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        scheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        scheme.surfaceContainer.copy(alpha = 0.88f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.04f),
                    ),
                ),
                shape = shape,
            )
            .padding(24.dp),
    ) {
        Column(content = content)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    contentScrollable: Boolean = false,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        GlassDialogSurface {
            if (icon != null) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.secondary,
                    ) { icon() }
                }
                Spacer(Modifier.padding(top = 8.dp))
            }
            if (title != null) {
                ProvideTextStyle(
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    ) { title() }
                }
            }

            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .then(if (contentScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                content = content,
            )

            if (buttons != null) {
                Spacer(Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = buttons,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        GlassDialogSurface(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                content = content,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ActionPromptDialog(
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleBar: (@Composable () -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        GlassDialogSurface {
            when {
                titleBar != null -> {
                    Box(modifier = Modifier.fillMaxWidth()) { titleBar() }
                }
                title != null -> {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.padding(top = 16.dp))

            Column(modifier = modifier.fillMaxWidth(), content = content)

            Spacer(Modifier.padding(top = 16.dp))

            if (confirmButton != null) {
                confirmButton()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onReset != null) {
                        TextButton(onClick = onReset) {
                            Text(text = stringResource(R.string.reset))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) {
                            Text(text = stringResource(android.R.string.cancel))
                        }
                    }
                    if (onConfirm != null) {
                        TextButton(onClick = onConfirm) {
                            Text(text = stringResource(android.R.string.ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(), // legacy
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    onDone: (String) -> Unit = {},

    // new multi-field support
    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,

    onDismiss: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val legacyFieldState = remember(initialTextFieldValue) { mutableStateOf(initialTextFieldValue) }

    LaunchedEffect(initialTextFieldValue) {
        legacyFieldState.value = initialTextFieldValue
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        contentScrollable = true,
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid = textFields?.all { isInputValid(it.second.text) }
                ?: isInputValid(legacyFieldState.value.text)

            TextButton(
                enabled = isValid,
                onClick = {
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(legacyFieldState.value.text)
                    }
                    onDismiss()
                },
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    ) {
        Column {
            if (textFields != null) {
                textFields.forEachIndexed { index, (label, value) ->
                    TextField(
                        value = value,
                        onValueChange = { onTextFieldsChange?.invoke(index, it) },
                        placeholder = { Text(label) },
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (onDoneMultiple != null) {
                                    onDoneMultiple(textFields.map { it.second.text })
                                    onDismiss()
                                }
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                }
            } else {
                TextField(
                    value = legacyFieldState.value,
                    onValueChange = { legacyFieldState.value = it },
                    placeholder = placeholder,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    colors = OutlinedTextFieldDefaults.colors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onDone(legacyFieldState.value.text)
                            onDismiss()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }

            extraContent?.invoke()
        }
    }
}

@Composable
fun AccountSettingsDialog(
    navController: NavController,
    onDismiss: () -> Unit,
    latestVersionName: String,
) {
    // The frosted chrome (rising spring, layered gradient, hairline rim, grab handle) lives in
    // LiquidGlassSheet so this sheet and the update sheet are literally the same material.
    LiquidGlassSheet(onDismiss = onDismiss) {
        AccountSettings(
            navController = navController,
            onClose = onDismiss,
            latestVersionName = latestVersionName,
        )
    }
}

@Composable
fun InfoLabel(
    text: String,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 8.dp),
) {
    Icon(
        painter = painterResource(id = R.drawable.info),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(4.dp),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
fun EditPlaylistDialog(
    initialName: String,
    initialThumbnailUrl: String?,
    fallbackThumbnails: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, thumbnailUrl: String?) -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var nameField by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    var thumbnailUrl by rememberSaveable { mutableStateOf(initialThumbnailUrl) }

    val previewThumbnails by remember(thumbnailUrl, fallbackThumbnails) {
        derivedStateOf {
            val custom = thumbnailUrl
            if (!custom.isNullOrBlank()) listOf(custom) else fallbackThumbnails
        }
    }

    fun releasePersistablePermissionIfPossible(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return
        if (uri.scheme != "content") return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    val pickCoverLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val old = thumbnailUrl
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (old != null && old != uri.toString()) {
                releasePersistablePermissionIfPossible(old)
            }
            thumbnailUrl = uri.toString()
        }

    val canSave by remember {
        derivedStateOf { nameField.text.isNotBlank() }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
        title = { Text(text = stringResource(R.string.edit_playlist)) },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(
                enabled = canSave,
                onClick = {
                    keyboardController?.hide()
                    onSave(nameField.text.trim(), thumbnailUrl?.takeUnless { it.isBlank() })
                    onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(modifier = Modifier.size(140.dp)) {
                val thumbnailSize = maxWidth
                val badgeSize = (thumbnailSize * 0.34f).coerceIn(36.dp, 48.dp)
                val badgePadding = (thumbnailSize * 0.06f).coerceIn(4.dp, 10.dp)
                val iconSize = (badgeSize * 0.46f).coerceIn(18.dp, 24.dp)

                PlaylistThumbnail(
                    thumbnails = previewThumbnails,
                    size = thumbnailSize,
                    placeHolder = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                tint = LocalContentColor.current.copy(alpha = 0.8f),
                                modifier = Modifier.size(thumbnailSize / 2),
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                )

                Surface(
                    onClick = { pickCoverLauncher.launch(arrayOf("image/*")) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(badgePadding)
                        .size(badgeSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.edit),
                            contentDescription = stringResource(R.string.change_playlist_cover),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(iconSize),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!thumbnailUrl.isNullOrBlank()) {
                Button(
                    onClick = {
                        releasePersistablePermissionIfPossible(thumbnailUrl)
                        thumbnailUrl = null
                    },
                ) {
                    Text(text = stringResource(R.string.remove_playlist_cover))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            TextField(
                value = nameField,
                onValueChange = { nameField = it },
                placeholder = { Text(text = stringResource(R.string.playlist_name)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!canSave) return@KeyboardActions
                        keyboardController?.hide()
                        onSave(nameField.text.trim(), thumbnailUrl?.takeUnless { it.isBlank() })
                        onDismiss()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
