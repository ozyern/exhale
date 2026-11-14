/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFCheckmark
import com.composables.sfsymbols.monochrome.SFXmark
import com.music.innertube.YouTube
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.db.entities.PlaylistEntity
import com.ozyern.exhale.extensions.isSyncEnabled
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Switch
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.logging.Logger

/** Full-height playlist creation sheet with a cover picker, title field, and sync option. */
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    val database = LocalDatabase.current.get()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val colors = IosTheme.colors

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()

    var title by remember { mutableStateOf(initialTextFieldValue.orEmpty()) }
    var cover by remember { mutableStateOf<Uri?>(null) }
    var syncedPlaylist by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val canSave = title.isNotBlank() && !saving

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cover = uri
    }

    // iOS sheet: rises from the bottom on a critically damped spring, leaves on a quick ease.
    val offset = remember { Animatable(1f) }
    LaunchedEffect(Unit) { offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f)) }
    fun close() {
        coroutineScope.launch {
            offset.animateTo(1f, tween(220))
            onDismiss()
        }
    }

    fun create() {
        if (!canSave) return
        saving = true
        val playlistName = title.trim()
        coroutineScope.launch(Dispatchers.IO) {
            val wantsSync = syncedPlaylist
            val browseId = when {
                !wantsSync -> null
                !isSignedIn -> {
                    Logger.getLogger("CreatePlaylistDialog").warning("Not signed in")
                    null
                }
                else -> runCatching { YouTube.createPlaylist(playlistName) }
                    .onFailure { Logger.getLogger("CreatePlaylistDialog").warning("Playlist sync failed: $it") }
                    .getOrNull()
            }

            val playlistEntity = try {
                val base = PlaylistEntity(
                    name = playlistName,
                    browseId = browseId,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                )
                // Persist the selected image before inserting so the playlist row
                // never points at a cover file that failed to copy.
                val thumbnail = cover?.let { persistPlaylistCover(context, it, base.id) }?.toString()
                base.copy(thumbnailUrl = thumbnail).also { playlist ->
                    // database.query{} is async; call insert() directly so the row
                    // exists before onPlaylistCreated runs.
                    database.insert(playlist)
                }
            } catch (error: Exception) {
                Logger.getLogger("CreatePlaylistDialog").warning("Local playlist creation failed: $error")
                withContext(Dispatchers.Main) {
                    saving = false
                    Toast.makeText(context, R.string.playlist_creation_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(
                        if (wantsSync && browseId == null) R.string.playlist_created_sync_failed
                        else R.string.playlist_created
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                onPlaylistCreated?.invoke(playlistEntity.id)
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = ::close)
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val sheetColor = if (colors.isDark) colors.secondarySystemBackground else colors.systemBackground
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f * (1f - offset.value))),
        ) {
            val travel = with(LocalDensity.current) { maxHeight.toPx() }
            Column(
                Modifier
                    .padding(top = statusTop + 10.dp)
                    .fillMaxSize()
                    .graphicsLayer { translationY = offset.value * travel }
                    .clip(ContinuousRoundedRectangle(topStart = 38.dp, topEnd = 38.dp, bottomEnd = 0.dp, bottomStart = 0.dp))
                    .background(sheetColor)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp)
                        .height(44.dp),
                ) {
                    SheetCircleButton(
                        onClick = ::close,
                        fill = colors.tertiarySystemFill,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        SymbolIcon(symbol = sf("SFXmark"), contentDescription = stringResource(R.string.cancel), size = 16.dp, tint = colors.label)
                    }
                    Text(
                        text = stringResource(R.string.new_playlist),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        letterSpacing = (-0.43).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.label,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    SheetCircleButton(
                        onClick = ::create,
                        enabled = canSave,
                        fill = if (canSave) IosTheme.tint else colors.tertiarySystemFill,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        SymbolIcon(symbol = sf("SFCheckmark"), contentDescription = stringResource(R.string.create_playlist), size = 18.dp, tint = if (canSave) Color.White else colors.tertiaryLabel)
                    }
                }

                // Cover: square well; the camera button picks artwork, which then fills it.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(180.dp)
                        .clip(ContinuousRoundedRectangle(12.dp))
                        .background(colors.tertiarySystemFill)
                        .clickable { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    if (cover != null) {
                        AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(IosTheme.tint),
                        ) {
                            SymbolIcon(symbol = sf("camera"), contentDescription = stringResource(R.string.choose_playlist_cover), size = 24.dp, tint = Color.White)
                        }
                    }
                }

                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                val fieldStyle = TextStyle(
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    letterSpacing = (-0.45).sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = colors.label,
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(top = 44.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (title.isEmpty()) {
                        Text(text = stringResource(R.string.playlist_title), style = fieldStyle.copy(color = colors.tertiaryLabel))
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = fieldStyle,
                        cursorBrush = SolidColor(IosTheme.tint),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { create() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(IosMetrics.SeparatorThickness)
                            .background(colors.separator),
                    )
                }

                if (allowSyncing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(52.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.sync_playlist),
                            fontSize = 15.sp,
                            letterSpacing = (-0.23).sp,
                            color = colors.label,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = syncedPlaylist,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    when {
                                        !isSignedIn && !syncedPlaylist -> Toast.makeText(
                                            context, context.getString(R.string.not_logged_in_youtube), Toast.LENGTH_SHORT,
                                        ).show()
                                        !context.isSyncEnabled() -> Toast.makeText(
                                            context, context.getString(R.string.sync_disabled), Toast.LENGTH_SHORT,
                                        ).show()
                                        else -> syncedPlaylist = !syncedPlaylist
                                    }
                                }
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.allows_for_sync_witch_youtube),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = colors.secondaryLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Circular icon button used in the sheet toolbar. */
@Composable
private fun SheetCircleButton(
    onClick: () -> Unit,
    fill: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick),
    ) { content() }
}
