/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.ozyern.exhale.App.Companion.forgetAccount
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AccountChannelHandleKey
import com.ozyern.exhale.constants.AccountEmailKey
import com.ozyern.exhale.constants.AccountNameKey
import com.ozyern.exhale.constants.DataSyncIdKey
import com.ozyern.exhale.constants.EnableUpdateNotificationKey
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.PoTokenKey
import com.ozyern.exhale.constants.SelectedYtmPlaylistsKey
import com.ozyern.exhale.constants.UseLoginForBrowse
import com.ozyern.exhale.constants.VisitorDataKey
import com.ozyern.exhale.constants.YtmSyncKey
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.utils.completed
import com.ozyern.exhale.innertube.utils.parseCookieString
import com.ozyern.exhale.ui.component.InfoLabel
import com.ozyern.exhale.ui.component.bounceClick
import com.ozyern.exhale.ui.component.SettingsDividerThickness
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.TokenEditorDialog
import com.ozyern.exhale.ui.component.TextFieldDialog
import com.ozyern.exhale.ui.component.liquidGlassSurface
import com.ozyern.exhale.ui.component.settingsDividerColor
import com.ozyern.exhale.ui.screens.buildLoginRoute
import com.ozyern.exhale.utils.Updater
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.HomeViewModel
import androidx.compose.animation.core.animateIntAsState
import com.ozyern.exhale.constants.ChipSortTypeKey
import com.ozyern.exhale.constants.LibraryFilter
import com.ozyern.exhale.ui.screens.Screens
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.viewmodels.AccountLibraryViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.AquamorphicStiffness

@Composable
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (poToken, onPoTokenChange) = rememberPreference(PoTokenKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)
    val (enableUpdateNotification, onEnableUpdateNotificationChange) = rememberPreference(
        EnableUpdateNotificationKey, defaultValue = false
    )

    val viewModel: HomeViewModel = hiltViewModel()
    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()

    val libraryViewModel: AccountLibraryViewModel = hiltViewModel()
    val librarySongs by libraryViewModel.songCount.collectAsState()
    val libraryArtists by libraryViewModel.artistCount.collectAsState()
    val libraryAlbums by libraryViewModel.albumCount.collectAsState()

    // Written before navigating so the library opens already filtered to whichever number was
    // tapped. Setting the same preference the library screen reads is the whole mechanism - no
    // argument to thread through the route, and the filter is where the user left it next time.
    // The long tail of the sheet is folded away until asked for - see `AccountMoreSettingsRow`.
    var showMoreSettings by rememberSaveable { mutableStateOf(false) }

    val (_, onLibraryFilterChange) = rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val openLibrary: (LibraryFilter) -> Unit = { filter ->
        onLibraryFilterChange(filter)
        onClose()
        navController.navigate(Screens.Library.route)
    }

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val hasUpdate = Updater.hasUpdate(latestVersionName, BuildConfig.VERSION_NAME)

    Column(
        // Transparent: the host (frosted glass sheet) paints the surface — an opaque
        // background here would kill the translucent iOS look.
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        AccountSheetTopBar(onClose = onClose)

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // One identity block, not two. The sheet used to open with a large centred avatar and
            // then repeat the same avatar, name and email in a card immediately underneath it —
            // a third of the sheet's height spent saying the same thing twice before the first
            // actionable row.
            AccountIdentityCard(
                isLoggedIn = isLoggedIn,
                accountName = accountName.ifEmpty { accountNamePref },
                accountEmail = accountEmail,
                accountImageUrl = accountImageUrl,
                onClick = {
                    onClose()
                    if (isLoggedIn) {
                        navController.navigate("account")
                    } else {
                        navController.navigate(buildLoginRoute())
                    }
                },
            )

            // What is actually in the library, directly under whose library it is. The sheet
            // used to go straight from a name to a row of navigation shortcuts, which meant the
            // one screen in the app that is entirely about *this account* said nothing about it.
            AccountLibraryStrip(
                songs = librarySongs,
                artists = libraryArtists,
                albums = libraryAlbums,
                onOpen = openLibrary,
            )

            // The three things people actually open this sheet for, as tiles rather than as rows
            // buried in three separate captioned groups further down.
            AccountQuickTiles(
                hasUpdate = hasUpdate,
                onSoundChem = { onClose(); navController.navigate("stats") },
                onHistory = { onClose(); navController.navigate("history") },
                onSettings = { onClose(); navController.navigate("settings") },
            )

            // Sign in / out is NOT a fourth tile.
            //
            // Two reasons it had to come out of that row. Four tiles across a phone left each one
            // about 80dp wide, which is not enough for "Sound Chem" to fit on the single line the
            // tile allows — and a destructive, irreversible action was sitting at identical weight
            // to "History", one stray thumb away from ending the session with no confirmation and
            // no undo. It is now a full-width row of its own, and signing out asks first.
            AccountSignInOutRow(
                isLoggedIn = isLoggedIn,
                onSignIn = {
                    onClose()
                    navController.navigate(buildLoginRoute())
                },
                onSignOut = { showSignOutConfirm = true },
            )

            if (showSignOutConfirm) {
                AlertDialog(
                    onDismissRequest = { showSignOutConfirm = false },
                    shape = RoundedCornerShape(28.dp),
                    title = {
                        Text(
                            text = stringResource(R.string.account_signout_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.account_signout_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showSignOutConfirm = false
                                onInnerTubeCookieChange("")
                                forgetAccount(context)
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.logout),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSignOutConfirm = false }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }

            if (hasUpdate) {
                SettingsSection {
                    UpdateAvailableItem(
                        latestVersion = latestVersionName,
                        onClick = { uriHandler.openUri(Updater.getLatestDownloadUrl()) }
                    )
                }
            }

            // Token Editor Dialog
            if (showTokenEditor) {
                TokenEditorDialog(
                    onDismiss = { showTokenEditor = false }
                )
            }

            // Everything from here down is a settings screen wearing a sheet.
            //
            // Four captioned groups - account toggles, notification permissions, integrations,
            // token - none of which is why anyone taps an avatar. The sheet is opened to see who
            // you are signed in as, to jump to History or Stats, or to sign out; those four
            // things were at the top and the other twelve rows pushed the whole thing to about
            // two and a half screens of scrolling, so the short answer to a short question was
            // buried in a long one.
            //
            // Folded behind one row rather than deleted: every one of them is still exactly where
            // it was, one tap further away, and the sheet now opens at the size of what it is for.
            AccountMoreSettingsRow(
                expanded = showMoreSettings,
                onToggle = { showMoreSettings = !showMoreSettings },
            )

            // Account Options Section
            AnimatedVisibility(
                visible = isLoggedIn && showMoreSettings,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SettingsSection(title = stringResource(R.string.account)) {
                    SettingsToggleItem(
                        icon = painterResource(R.drawable.add_circle),
                        title = stringResource(R.string.more_content),
                        subtitle = stringResource(R.string.use_login_for_browse_desc),
                        checked = useLoginForBrowse,
                        onCheckedChange = {
                            YouTube.useLoginForBrowse = it
                            onUseLoginForBrowseChange(it)
                        }
                    )

                    SettingsRowDivider()

                    SettingsToggleItem(
                        icon = painterResource(R.drawable.cached),
                        title = stringResource(R.string.yt_sync),
                        checked = ytmSync,
                        onCheckedChange = onYtmSyncChange
                    )
                }
            }

            // Notifications Section (relocated into the Accounts area)
            AnimatedVisibility(
                visible = showMoreSettings,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsSection(title = stringResource(R.string.permission_notifications_title)) {
                SettingsToggleItem(
                    icon = painterResource(R.drawable.notifications),
                    title = stringResource(R.string.enable_update_notification),
                    subtitle = stringResource(R.string.enable_update_notification_desc),
                    checked = enableUpdateNotification,
                    onCheckedChange = onEnableUpdateNotificationChange
                )

                SettingsRowDivider()

                SettingsClickableItem(
                    icon = painterResource(R.drawable.notifications),
                    title = stringResource(R.string.notification_settings),
                    subtitle = stringResource(R.string.permission_notifications_desc),
                    onClick = {
                        // Open the system notification settings for this app.
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                        ).apply {
                            putExtra(
                                android.provider.Settings.EXTRA_APP_PACKAGE,
                                context.packageName
                            )
                        }
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            // Sync & Integration Section
            SettingsSection(title = stringResource(R.string.integration)) {
                SettingsClickableItem(
                    icon = painterResource(R.drawable.playlist_add),
                    title = stringResource(R.string.select_playlist_to_sync),
                    onClick = { showPlaylistDialog = true }
                )

                SettingsRowDivider()

                SettingsClickableItem(
                    icon = painterResource(R.drawable.integration),
                    title = stringResource(R.string.integration),
                    subtitle = "Discord, Last.fm, ListenBrainz",
                    onClick = {
                        onClose()
                        navController.navigate("settings/integration")
                    }
                )

                SettingsRowDivider()

                SettingsClickableItem(
                    icon = painterResource(R.drawable.fire),
                    title = stringResource(R.string.music_together),
                    onClick = {
                        onClose()
                        navController.navigate("settings/music_together")
                    }
                )
            }

            // Advanced Section
            SettingsSection(title = stringResource(R.string.misc)) {
                SettingsClickableItem(
                    icon = painterResource(R.drawable.token),
                    title = when {
                        !isLoggedIn -> stringResource(R.string.advanced_login)
                        showToken -> stringResource(R.string.token_shown)
                        else -> stringResource(R.string.token_hidden)
                    },
                    onClick = {
                        if (!isLoggedIn) showTokenEditor = true
                        else if (!showToken) showToken = true
                        else showTokenEditor = true
                    }
                )
            }

            }
            }

            // App Version Footer
            AppVersionFooter()

            Spacer(Modifier.height(8.dp))
        }
    }

    // Playlist Selection Dialog
    if (showPlaylistDialog) {
        PlaylistSelectionDialog(
            onDismiss = { showPlaylistDialog = false }
        )
    }
}

/**
 * The one row that stands for the four settings groups underneath it.
 *
 * Deliberately quieter than the tiles above: no accent puck, no chevron pointing off the sheet.
 * A caret that rotates in place is the honest glyph for something that opens *here*, and the row
 * is the last thing on the sheet precisely so it reads as "and the rest" rather than as a peer of
 * History and Stats.
 */
@Composable
private fun AccountMoreSettingsRow(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val caret by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "accountMoreCaret",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onToggle, shape = RoundedCornerShape(20.dp))
            .liquidGlassSurface(RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.tune),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.account_more_settings),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.account_more_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.expand_more),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = caret },
        )
    }
}

/** Just a close affordance. The sheet's own grab handle is the primary dismissal. */
@Composable
private fun AccountSheetTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.account),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // A glass disc, not a Material IconButton on a flat 8% grey circle. On a translucent
        // sheet that grey puck is the one element that reads as painted-on rather than as part
        // of the same pane of glass as everything under it.
        Box(
            modifier = Modifier
                .size(34.dp)
                .bounceClick(onClick = onClose, shape = CircleShape)
                .liquidGlassSurface(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/**
 * The identity block: portrait, name, second line, chevron.
 *
 * Left-aligned and compact rather than a centred column with a 112dp halo. A sheet has a hard
 * height budget and everything below this has to fit in what is left; a centred hero spends that
 * budget on the one piece of information the user already knows.
 */
@Composable
private fun AccountIdentityCard(
    isLoggedIn: Boolean,
    accountName: String,
    accountEmail: String,
    accountImageUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The app's tap physics rather than a bare `clickable`. A Material ripple washing
            // across a translucent glass plate is the single cheapest-looking interaction in
            // any glass UI — it paints an opaque grey circle over the thing that is supposed
            // to be a pane of glass.
            .bounceClick(onClick = onClick, shape = RoundedCornerShape(20.dp))
            .liquidGlassSurface(RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The portrait sits in a gradient ring rather than behind a radial haze. The old halo was
        // a soft primary-coloured cloud bleeding out to transparent — on a translucent sheet that
        // reads as a smudge behind the avatar, not as a deliberate frame.
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ringColor = if (isLoggedIn) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(ringColor.copy(alpha = 0.75f), ringColor.copy(alpha = 0.18f)),
                        )
                    )
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoggedIn && accountImageUrl != null) {
                    AsyncImage(
                        model = accountImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(ringColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.account),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = ringColor,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isLoggedIn && accountName.isNotEmpty()) {
                    accountName
                } else {
                    stringResource(R.string.account_signed_out_title)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    isLoggedIn && accountEmail.isNotEmpty() -> accountEmail
                    isLoggedIn -> stringResource(R.string.account_signed_in_subtitle)
                    else -> stringResource(R.string.account_signed_out_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        // The chevron gets its own disc so it reads as a target. A bare 40%-alpha glyph floating
        // at the edge of a card is the detail that makes a row look unfinished.
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The library totals: songs, artists, albums, as one glass plate split into three.
 *
 * Numbers first and large, labels underneath and small, because the number is the content and the
 * label is the caption — the other way round is a form field. Each third navigates to the library
 * already filtered to what it counts, so the strip is a shortcut and not just a readout; a stat
 * you cannot act on belongs on a stats screen, and this sheet has a tile for that one.
 *
 * Hairline dividers rather than three separate cards: at this width three cards would be three
 * 100dp plates with 10dp of glass showing between them, which reads as a control row. One plate
 * with rules in it reads as one fact in three parts, which is what it is.
 */
@Composable
private fun AccountLibraryStrip(
    songs: Int,
    artists: Int,
    albums: Int,
    onOpen: (LibraryFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountLibraryStat(
            value = songs,
            label = stringResource(R.string.songs),
            onClick = { onOpen(LibraryFilter.SONGS) },
            modifier = Modifier.weight(1f),
        )
        AccountLibraryStatDivider()
        AccountLibraryStat(
            value = artists,
            label = stringResource(R.string.artists),
            onClick = { onOpen(LibraryFilter.ARTISTS) },
            modifier = Modifier.weight(1f),
        )
        AccountLibraryStatDivider()
        AccountLibraryStat(
            value = albums,
            label = stringResource(R.string.albums),
            onClick = { onOpen(LibraryFilter.ALBUMS) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AccountLibraryStat(
    value: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // No plate of its own — it is a third of one — so the ripple is given the
            // inner radius rather than the plate's, which would round past the dividers.
            .bounceClick(onClick = onClick, shape = RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Animated, because these tick up as a sync lands while the sheet is open, and a number
        // that jumps from 0 to 1,412 with no travel reads as a glitch rather than as an import.
        val shown by animateIntAsState(
            targetValue = value,
            animationSpec = tween(durationMillis = 650),
            label = "libraryStat",
        )
        Text(
            text = shown.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountLibraryStatDivider() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)),
    )
}

/**
 * Three square tiles under the identity card.
 *
 * These destinations used to be scattered across three separate captioned groups, each one a
 * full-width row indistinguishable from a preference toggle. As tiles they are the sheet's answer
 * to "why did I open this" — glanceable, reachable with a thumb, and done in one tap.
 *
 * Three, not four: at four across a phone each tile was about 80dp wide, and a 42dp puck plus a
 * one-line label does not fit "Sound Chem" in 80dp. Sign in/out — the tile that was dropped — was
 * also the one that did not belong in a row of navigation shortcuts. See [AccountSignInOutRow].
 */
@Composable
private fun AccountQuickTiles(
    hasUpdate: Boolean,
    onSoundChem: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccountTile(
            icon = painterResource(R.drawable.stats),
            label = stringResource(R.string.sound_chem),
            accent = MaterialTheme.colorScheme.primary,
            onClick = onSoundChem,
            modifier = Modifier.weight(1f),
        )
        AccountTile(
            icon = painterResource(R.drawable.history),
            label = stringResource(R.string.history),
            accent = MaterialTheme.colorScheme.secondary,
            onClick = onHistory,
            modifier = Modifier.weight(1f),
        )
        AccountTile(
            icon = painterResource(R.drawable.settings),
            label = stringResource(R.string.settings),
            accent = MaterialTheme.colorScheme.tertiary,
            showBadge = hasUpdate,
            onClick = onSettings,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The session action, as its own full-width row.
 *
 * Signing out is destructive and irreversible from this sheet's point of view; signing in is the
 * single most important thing a signed-out user can do here. Neither is a peer of "History", which
 * is what being a fourth tile made them.
 */
@Composable
private fun AccountSignInOutRow(
    isLoggedIn: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val accent = if (isLoggedIn) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(
                onClick = if (isLoggedIn) onSignOut else onSignIn,
                shape = RoundedCornerShape(20.dp),
            )
            .liquidGlassSurface(RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.16f))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (isLoggedIn) R.drawable.logout else R.drawable.login
                ),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = stringResource(if (isLoggedIn) R.string.logout else R.string.login),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isLoggedIn) accent else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        if (!isLoggedIn) {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AccountTile(
    icon: Painter,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
) {
    Column(
        modifier = modifier
            .bounceClick(onClick = onClick, shape = RoundedCornerShape(20.dp))
            // Neutral glass, coloured glyph. The tiles used to tint the ENTIRE plate with their
            // accent, which put four differently-coloured cards in a row — a 2010s dashboard,
            // and the single most dated thing on the sheet. The colour belongs on the glyph,
            // where it identifies the destination; the plate is the same material as every
            // other surface here.
            .liquidGlassSurface(RoundedCornerShape(20.dp))
            .padding(vertical = 15.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The glyph sits in its own tinted puck rather than floating loose on the tile.
        // A bare icon on a card reads as a list row that lost its text; a puck gives the
        // tile a focal object and is what makes a grid of them scan as controls.
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.16f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp),
                )
            }
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            // labelSmall at 6dp of horizontal padding was cramped enough that three of the four
            // labels wrapped to two lines. One line, readable, with the room to stay that way.
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            // iOS grouped-list caption: small, tracked, muted. It used to be `titleLarge` bold,
            // which inside a sheet competed with the sheet's OWN title — three or four headings
            // all shouting at the same weight, so nothing read as the top of the hierarchy.
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 8.dp)
            )
        }

        // Grouped inset list: a Column clipped to the shared 16dp corner over a translucent tint
        // so the sheet's frosted glass reads through. NO Material Card — no elevation, no border.
        // Rows inside sit flush; callers place thin dividers only BETWEEN items (never at the very
        // top or bottom of the group) via [SettingsRowDivider].
        // The shared glass plate, the same one the Sound Chem deck is built from: gradient fill,
        // diagonal sheen, hairline rim. This used to be a flat 5% ink wash, which on the sheet's
        // already-translucent backdrop came out as a barely-there grey smudge — the groups did not
        // read as plates so much as as slightly dirty patches of the sheet.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(RoundedCornerShape(SettingsGroupCornerRadius))
        ) {
            content()
        }
    }
}

/**
 * The hairline between two rows of an inset group.
 *
 * Indented to where the row's *text* starts, not to the row's edge — that is the detail that makes
 * a grouped list read as iOS rather than as Material. The indent is derived from the row metrics
 * below (16dp leading padding + 40dp icon tile + 14dp gap) instead of being a magic number, so it
 * cannot silently fall out of alignment if the rows are ever re-padded.
 *
 * Never emitted at the top or bottom of a group; the clipped corners are the boundary there.
 */
private val AccountRowHorizontalPadding = 16.dp
private val AccountRowIconSize = 40.dp
private val AccountRowIconGap = 14.dp
private val AccountRowTextIndent =
    AccountRowHorizontalPadding + AccountRowIconSize + AccountRowIconGap

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = AccountRowTextIndent),
        thickness = SettingsDividerThickness,
        color = settingsDividerColor(),
    )
}

@Composable
private fun SettingsClickableItem(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    showBadge: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = AccountRowHorizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Container
        Box(
            modifier = Modifier
                .size(AccountRowIconSize)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (showBadge) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = MaterialTheme.colorScheme.error)
                    }
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(AccountRowIconGap))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingsToggleItem(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .padding(horizontal = AccountRowHorizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Container
        Box(
            modifier = Modifier
                .size(AccountRowIconSize)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.width(AccountRowIconGap))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LiquidToggle(
            checked = checked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            }
        )
    }
}

@Composable
private fun UpdateAvailableItem(
    latestVersion: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AccountRowHorizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Container with gradient
        Box(
            modifier = Modifier
                .size(AccountRowIconSize)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            BadgedBox(
                badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.error)
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.update),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(AccountRowIconGap))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.new_version_available),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = latestVersion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = stringResource(R.string.update_text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AppVersionFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}


@Composable
private fun PlaylistSelectionDialog(onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val (initialSelected, _) = rememberPreference(SelectedYtmPlaylistsKey, "")
    val selectedList = remember { mutableStateListOf<String>() }

    LaunchedEffect(initialSelected) {
        selectedList.clear()
        if (initialSelected.isNotEmpty()) {
            selectedList.addAll(
                initialSelected.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
        }
    }

    var loading by remember { mutableStateOf(true) }
    val playlists = remember { mutableStateListOf<com.ozyern.exhale.innertube.models.PlaylistItem>() }

    LaunchedEffect(Unit) {
        loading = true
        com.ozyern.exhale.innertube.YouTube
            .library("FEmusic_liked_playlists")
            .completed()
            .onSuccess { page ->
                playlists.clear()
                playlists.addAll(
                    page.items
                        .filterIsInstance<com.ozyern.exhale.innertube.models.PlaylistItem>()
                        .filterNot { it.id == "LM" || it.id == "SE" }
                        .reversed()
                )
            }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(
                onClick = {
                    com.ozyern.exhale.utils.PreferenceStore.launchEdit(context.dataStore) {
                        this[SelectedYtmPlaylistsKey] = selectedList.joinToString(",")
                    }
                    onDismiss()
                }
            ) {
                Text(
                    text = stringResource(R.string.save),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel_button))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.select_playlist_to_sync),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {

                    val density = LocalDensity.current
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        stroke = Stroke(
                            width = with(density) { 2.dp.toPx() }
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(playlists) { pl ->
                        val isSelected = selectedList.contains(pl.id)
                        val backgroundColor by animateColorAsState(
                            targetValue = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else
                                Color.Transparent,
                            label = "playlistItemColor"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(backgroundColor)
                                .clickable {
                                    if (isSelected) selectedList.remove(pl.id)
                                    else selectedList.add(pl.id)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) selectedList.add(pl.id)
                                    else selectedList.remove(pl.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(Modifier.width(8.dp))

                            AsyncImage(
                                model = pl.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )

                            Spacer(Modifier.width(12.dp))

                            Text(
                                text = pl.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}
