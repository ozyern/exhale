/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import com.ozyern.exhale.ui.component.PreferenceGroupTitle
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.R
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.datastore.preferences.core.stringPreferencesKey
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.constants.*
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.my.kizzy.rpc.KizzyRPC
import timber.log.Timber
import kotlinx.coroutines.*
import com.ozyern.exhale.utils.ArtworkStorage

enum class ActivitySource { ARTIST, ALBUM, SONG, APP }

/**
 * Sign in to Discord, or out of it.
 *
 * A filled capsule in Discord's own blurple when there is no account yet - this is the one action
 * the page exists for until it is done - and a quiet red-on-tint one once there is, because logging
 * out is destructive and should not be the loudest thing on a page full of settings.
 */
@Composable
private fun DiscordAccountAction(
    signedIn: Boolean,
    onClick: () -> Unit,
) {
    val blurple = Color(0xFF5865F2)
    val accent = if (signedIn) MaterialTheme.colorScheme.error else blurple
    val container = if (signedIn) accent.copy(alpha = 0.14f) else accent
    val ink = if (signedIn) accent else Color.White
    Box(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (signedIn) R.string.action_logout else R.string.action_login,
            ),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val song by playerConnection.currentSong.collectAsState(null)
    val playbackState by playerConnection.playbackState.collectAsState()
    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    // Track last RPC timestamps to detect when RPC progress bar reaches the end.
    // These are now owned by DiscordPresenceManager; read their current values here.
    val lastRpcStartTime = DiscordPresenceManager.lastRpcStartTime
    val lastRpcEndTime = DiscordPresenceManager.lastRpcEndTime
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var infoDismissed by rememberPreference(DiscordInfoDismissedKey, false)

    LaunchedEffect(discordToken) {
        val token = discordToken
        if (token.isNotEmpty()) {
            // Run the network call inside this LaunchedEffect coroutine so it is
            // cancelled automatically if the composable leaves the composition.
            try {
                withContext(Dispatchers.IO) {
                    // KizzyRPC.getUserInfo may throw network/socket exceptions when the
                    // app is backgrounded or network drops; catch them to avoid crashing.
                    KizzyRPC.getUserInfo(token)
                }.onSuccess {
                    discordUsername = it.username
                    discordName = it.name
                }
            } catch (e: Exception) {
                // Log and ignore network errors (e.g. SocketException on resume).
                Timber.tag("DiscordSettings").w(e, "getUserInfo failed")
            }
        }
    }

    val (discordRPC, onDiscordRPCChange) = rememberPreference(
        key = EnableDiscordRPCKey,
        defaultValue = true
    )

    LaunchedEffect(discordToken, discordRPC) {
        if (discordRPC && discordToken.isNotBlank()) {
            Timber.tag("DiscordSettings").d("RPC enabled with token, MusicService will handle start")
            // DiscordPresenceManager.start(
            //     context = context,
            //     token = discordToken,
            //     songProvider = { song },
            //     positionProvider = { playerConnection.player.currentPosition },
            //     isPausedProvider = {
            //         val isPlaying = playerConnection.player.playWhenReady &&
            //                 playerConnection.player.playbackState == STATE_READY
            //         !isPlaying
            //     },
            //     intervalProvider = { getPresenceIntervalMillis(context) }
            // )
        } else {
            // user disabled RPC or cleared token -> ensure manager is stopped
            Timber.tag("DiscordSettings").d("RPC disabled or no token, stopping manager")
            DiscordPresenceManager.stop()
        }
    }

    val isLoggedIn = remember(discordToken) { discordToken.isNotEmpty() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                // Below the content, not around the viewport: the page scrolls on under the dock.
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
        ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

    // Developer debug moved to DebugSettings (Settings -> Misc)

        // Sign in, switch it on, and that is the page.
        //
        // What was here was a control panel: an activity-type dropdown, three "what goes in the
        // name/details/state" pickers, large- and small-image sources with custom URL fields each,
        // large-text sources, two button labels, an update interval with its own unit selector, a
        // manual refresh and two status dropdowns. The desktop build has none of it and produces a
        // better presence, because there is one right answer for every one of those questions -
        // the song, the artist, the album art - and the defaults below are it. Everything removed
        // still has its preference key and its default, so a presence built by an older version
        // keeps working; there is simply nothing left to get wrong.
        PreferenceGroupTitle(title = stringResource(R.string.account))

        var showLogoutConfirm by remember { mutableStateOf(false) }

        PreferenceEntry(
            title = {
                Text(
                    text = if (isLoggedIn) discordName else stringResource(R.string.not_logged_in),
                    modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f),
                )
            },
            description = if (discordUsername.isNotEmpty()) "@$discordUsername" else null,
            icon = { Icon(painterResource(R.drawable.discord), null) },
            trailingContent = {
                // One capsule in the app's own style. `OutlinedButton` is Material's, and on a page
                // of frosted rows it was the only stroked rectangle on screen.
                DiscordAccountAction(
                    signedIn = isLoggedIn,
                    onClick = {
                        if (isLoggedIn) {
                            showLogoutConfirm = true
                        } else {
                            navController.navigate("settings/discord/login")
                        }
                    },
                )
            },
        )

        if (showLogoutConfirm) {
            IosAlert(
                title = stringResource(R.string.logout_confirm_title),
                message = stringResource(R.string.logout_confirm_message),
                confirmText = stringResource(R.string.logout_confirm_yes),
                dismissText = stringResource(R.string.logout_confirm_no),
                destructive = true,
                onConfirm = {
                    discordName = ""
                    discordToken = ""
                    discordUsername = ""
                    showLogoutConfirm = false
                },
                onDismiss = { showLogoutConfirm = false },
            )
        }

        PreferenceGroupTitle(title = stringResource(R.string.options))

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_discord_rpc)) },
            description = stringResource(R.string.discord_information),
            icon = { Icon(painterResource(R.drawable.discord), null) },
            checked = discordRPC,
            onCheckedChange = onDiscordRPCChange,
            isEnabled = isLoggedIn,
        )

        var showWhenPaused by rememberPreference(
            key = DiscordShowWhenPausedKey,
            defaultValue = false,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.discord_show_when_paused)) },
            description = stringResource(R.string.discord_show_when_paused_desc),
            icon = { Icon(painterResource(R.drawable.ic_pause_white), null) },
            checked = showWhenPaused,
            onCheckedChange = { showWhenPaused = it },
            isEnabled = discordRPC,
        )

        val (advancedOn) = rememberPreference(com.ozyern.exhale.constants.DiscordAdvancedKey, false)
        PreferenceEntry(
            title = { Text("Advanced") },
            description = if (advancedOn) "On — your own presence" else "Off — Exhale, the song and the artist",
            icon = { Icon(painterResource(R.drawable.tune), null) },
            onClick = { navController.navigate("settings/discord/advanced") },
            isEnabled = discordRPC,
        )

        // What the presence is made of: the standard answers, or Advanced's when it is on.
        val (advName) = rememberPreference(DiscordActivityNameKey, "APP")
        val (advDetails) = rememberPreference(DiscordActivityDetailsKey, "SONG")
        val (advState) = rememberPreference(DiscordActivityStateKey, "ARTIST")
        val (advType) = rememberPreference(DiscordActivityTypeKey, "LISTENING")
        val (advLarge) = rememberPreference(DiscordLargeImageTypeKey, "thumbnail")
        val (advLargeUrl) = rememberPreference(DiscordLargeImageCustomUrlKey, "")
        val (advSmall) = rememberPreference(DiscordSmallImageTypeKey, "artist")
        val (advSmallUrl) = rememberPreference(DiscordSmallImageCustomUrlKey, "")
        fun source(v: String, fallback: ActivitySource) =
            runCatching { ActivitySource.valueOf(v) }.getOrDefault(fallback)
        val nameSource = if (advancedOn) source(advName, ActivitySource.APP) else ActivitySource.APP
        val detailsSource = if (advancedOn) source(advDetails, ActivitySource.SONG) else ActivitySource.SONG
        val stateSource = if (advancedOn) source(advState, ActivitySource.ARTIST) else ActivitySource.ARTIST
        val activityType = if (advancedOn) advType else "LISTENING"
        val largeImageType = if (advancedOn) advLarge else "thumbnail"
        val largeImageCustomUrl = if (advancedOn) advLargeUrl else ""
        val smallImageType = if (advancedOn) advSmall else "artist"
        val smallImageCustomUrl = if (advancedOn) advSmallUrl else ""
        val button1Enabled = true
        val button2Enabled = true

        PreferenceGroupTitle(title = stringResource(R.string.preview))

    // Compute whether the player is currently playing so the preview progress can run.
    val playerIsPlayingForPreview = playerConnection.player.playWhenReady && playbackState == STATE_READY

    RichPresence(
        song,
        currentPlaybackTimeMillis = playerConnection.player.currentPosition,
        nameSource = nameSource,
        detailsSource = detailsSource,
        stateSource = stateSource,
        activityType = activityType,
        largeImageType = largeImageType,
        largeImageCustomUrl = largeImageCustomUrl,
        smallImageType = smallImageType,
        smallImageCustomUrl = smallImageCustomUrl,
        button1Enabled = button1Enabled,
        button2Enabled = button2Enabled,
        isPlaying = playerConnection.player.isPlaying
    )
}

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.discord_integration)) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
        actions = {
            var threeDotMenuExpanded by remember { mutableStateOf(false) }

            IconButton(onClick = { threeDotMenuExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null
                )
            }

            DropdownMenu(
                expanded = threeDotMenuExpanded,
                onDismissRequest = { threeDotMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Advanced") },
                    onClick = {
                        threeDotMenuExpanded = false
                        navController.navigate("settings/discord/advanced")
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.tune),
                            contentDescription = null
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.experiment_settings)) },
                    onClick = {
                        threeDotMenuExpanded = false
                        navController.navigate("settings/discord/experimental")
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.experiment),
                            contentDescription = null
                        )
                    }
                )
            }
        }
      )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySourceDropdown(
    title: String,
    iconRes: Int,
    selected: ActivitySource,
    onChange: (ActivitySource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp)
    ) {
        TextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = { Icon(painterResource(iconRes), null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ActivitySource.values().forEach { source ->
                DropdownMenuItem(
                    text = { Text(source.name) },
                    onClick = {
                        onChange(source)
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
fun EditablePreference(
    title: String,
    iconRes: Int,
    value: String,
    defaultValue: String,
    onValueChange: (String) -> Unit,
    description: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    PreferenceEntry(
        title = { Text(title) },
        description = description ?: if (value.isEmpty()) defaultValue else value,
        icon = { Icon(painterResource(iconRes), null) },
        trailingContent = {
            TextButton(onClick = { showDialog = true }) { Text("Edit") }
        }
    )
    if (showDialog) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(if (text.isBlank()) "" else text)
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
            title = { Text("Edit $title") },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(defaultValue) },
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        )
    }
}

@Composable
fun RichPresence(
    song: Song?,
    currentPlaybackTimeMillis: Long = 0L,
    nameSource: ActivitySource = ActivitySource.APP,
    detailsSource: ActivitySource = ActivitySource.SONG,
    stateSource: ActivitySource = ActivitySource.ARTIST,
    activityType: String = "LISTENING",
    largeImageType: String = "thumbnail",
    largeImageCustomUrl: String = "",
    smallImageType: String = "artist",
    smallImageCustomUrl: String = "",
    button1Enabled: Boolean = true,
    button2Enabled: Boolean = true,
    isPlaying: Boolean = false,
) {
    val context = LocalContext.current

    fun resolveUrl(source: String, song: Song?, custom: String): String? {
    return when (source.lowercase()) {
        "songurl" -> song?.id?.let { "https://music.youtube.com/watch?v=$it" }
        "artisturl" -> song?.artists?.firstOrNull()?.id?.let { "https://music.youtube.com/channel/$it" }
        "albumurl" -> song?.album?.playlistId?.let { "https://music.youtube.com/playlist?list=$it" }
        "custom" -> if (custom.isNotBlank()) custom else null
        else -> null
    }
   }

   val (button1Label) = rememberPreference(DiscordActivityButton1LabelKey, "Listen on YouTube Music")
   val (button1Enabled) = rememberPreference(DiscordActivityButton1EnabledKey, true)

   val (button2Label) = rememberPreference(DiscordActivityButton2LabelKey, "Go to Exhale")
   val (button2Enabled) = rememberPreference(DiscordActivityButton2EnabledKey, true)

// Button URL sources + custom
   val (button1UrlSource) = rememberPreference(DiscordActivityButton1UrlSourceKey, "songurl")
   val (button1CustomUrl) = rememberPreference(DiscordActivityButton1CustomUrlKey, "")

   val (button2UrlSource) = rememberPreference(DiscordActivityButton2UrlSourceKey, "custom")
   val (button2CustomUrl) = rememberPreference(DiscordActivityButton2CustomUrlKey, "https://github.com/ozyern/Exhale")

// Large text source + custom
   val (largeTextSource) = rememberPreference(DiscordLargeTextSourceKey, "album")
   val (largeTextCustom) = rememberPreference(DiscordLargeTextCustomKey, "")

    val previewLargeText = when (largeTextSource) {
    "song" -> song?.song?.title ?: "Song name"
    "artist" -> song?.artists?.firstOrNull()?.name ?: "Artist"
    "album" -> song?.song?.albumName ?: song?.album?.title ?: "Album"
    "app" -> stringResource(R.string.app_name)
    "custom" -> largeTextCustom.ifBlank { "Custom text" }
    "dontshow" -> null
    else -> song?.song?.albumName ?: song?.album?.title
    }
    val resolvedButton1Url = resolveUrl(button1UrlSource, song, button1CustomUrl)
    val resolvedButton2Url = resolveUrl(button2UrlSource, song, button2CustomUrl)
    val activityVerb = when (activityType.uppercase()) {
    "PLAYING" -> "Playing"
    "LISTENING" -> "Listening to"
    "WATCHING" -> "Watching"
    "STREAMING" -> "Streaming"
    "COMPETING" -> "Competing in"
    else -> activityType.replaceFirstChar { 
        if (it.isLowerCase()) it.titlecase() else it.toString() 
       }
    }

    val previewTitle = when (nameSource) {
    ActivitySource.ARTIST -> "$activityVerb ${song?.artists?.firstOrNull()?.name ?: "Artist"}"
    ActivitySource.ALBUM -> "$activityVerb ${song?.album?.title ?: song?.song?.albumName ?: "Album"}"
    ActivitySource.SONG -> "$activityVerb ${song?.song?.title ?: "Song"}"
    ActivitySource.APP -> "$activityVerb Exhale"
   }


    PreferenceEntry(
        title = {
            Text(
            text = stringResource(R.string.preview),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
            )
        },
        content = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = previewTitle,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(108.dp)) {
                            AsyncImage(
                                model = when (largeImageType) {
                                    "thumbnail" -> song?.song?.thumbnailUrl
                                    "artist" -> song?.artists?.firstOrNull()?.thumbnailUrl
                                    "appicon" -> "https://raw.githubusercontent.com/ozyern/Exhale/refs/heads/master/assets/icon.png"
                                    "custom" -> largeImageCustomUrl.ifBlank { song?.song?.thumbnailUrl }
                                    else -> song?.song?.thumbnailUrl
                                },
                                contentDescription = null,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .align(Alignment.TopStart)
                                    .run {
                                        if (song == null) border(
                                            2.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            RoundedCornerShape(12.dp)
                                        ) else this
                                    },
                            )
                            val songThumb = song?.song?.thumbnailUrl
                            val artistThumb = song?.artists?.firstOrNull()?.thumbnailUrl

                            // Fix: Don't fallback from artist to song thumbnail - each source should be independent
                            val smallModel = when (smallImageType.lowercase()) {
                                "thumbnail" -> songThumb  // Only show song thumbnail, no fallback
                                "artist" -> artistThumb   // Only show artist thumbnail, no fallback to song
                                "appicon" -> "https://raw.githubusercontent.com/ozyern/Exhale/refs/heads/master/assets/icon.png"
                                "custom" -> smallImageCustomUrl.takeIf { it.isNotBlank() } ?: songThumb  // Custom with fallback to song only
                                "dontshow", "none" -> null
                                else -> artistThumb  // Default to artist without fallback
                            }
                            smallModel?.let {
                                Box(
                                    modifier = Modifier
                                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                                        .padding(2.dp)
                                        .align(Alignment.BottomEnd),
                                ) {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        ) {
                            Text(
                                text = song?.song?.title ?: "Song Title",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // Compute a preview for the "state" line according to the selected stateSource
                            val previewState = when (stateSource) {
                                ActivitySource.ARTIST -> song?.artists?.joinToString { it.name } ?: "Artist"
                                ActivitySource.ALBUM -> song?.song?.albumName ?: song?.album?.title ?: song?.song?.title ?: "Unknown Album"
                                ActivitySource.SONG -> song?.song?.title ?: "Song"
                                ActivitySource.APP -> stringResource(R.string.app_name)
                            }

                            Text(
                                text = previewState,
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            previewLargeText?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (song != null) {
                                SongProgressBar(
                                    currentTimeMillis = currentPlaybackTimeMillis,
                                    durationMillis = song.song.duration * 1000L,
                                    isPlaying = isPlaying,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = button1Enabled && button1Label.isNotBlank()) {
                        Button(
                            enabled = !resolvedButton1Url.isNullOrBlank(),
                            onClick = {
                              resolvedButton1Url?.let {
                              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                         }
                     },
                        modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(button1Label.ifBlank { "Listen on YouTube Music" })
                        }
                    }

                    AnimatedVisibility(visible = button2Enabled && button2Label.isNotBlank()) {
                        Button(
                            enabled = !resolvedButton2Url.isNullOrBlank(),
                            onClick = {
                              resolvedButton2Url?.let {
                              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        }
                     },
                        modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(button2Label.ifBlank { "View Album" })
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SongProgressBar(
    currentTimeMillis: Long,
    durationMillis: Long,
    isPlaying: Boolean = false
) {
    var displayedTime by remember { mutableStateOf(currentTimeMillis) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                com.ozyern.exhale.utils.awaitAppVisible()
                delay(500)
                displayedTime += 500
                if (displayedTime >= durationMillis) {
                    displayedTime = durationMillis
                    break
                }
            }
        }
    }

    val progress = if (durationMillis > 0) {
        displayedTime.toFloat() / durationMillis
    } else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = makeTimeString(displayedTime),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontSize = 12.sp
            )
            Text(
                text = makeTimeString(durationMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontSize = 12.sp
            )
        }
    }
}
