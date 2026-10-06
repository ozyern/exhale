/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.PreferenceGroup
import android.annotation.SuppressLint
import androidx.compose.ui.graphics.isSpecified
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.component.PreferenceGroupTitle
import com.ozyern.exhale.ui.component.PreferenceEntry
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.ui.component.settingsIconPuck
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.TogetherAllowGuestsToAddTracksKey
import com.ozyern.exhale.constants.TogetherAllowGuestsToControlPlaybackKey
import com.ozyern.exhale.constants.TogetherDefaultPortKey
import com.ozyern.exhale.constants.TogetherDisplayNameKey
import com.ozyern.exhale.constants.TogetherLastJoinLinkKey
import com.ozyern.exhale.constants.TogetherRelayUrlKey
import com.ozyern.exhale.constants.TogetherRequireHostApprovalToJoinKey
import com.ozyern.exhale.constants.TogetherWelcomeShownKey
import com.ozyern.exhale.together.TogetherLink
import com.ozyern.exhale.together.TogetherOnlineEndpoint
import com.ozyern.exhale.together.TogetherRole
import com.ozyern.exhale.together.TogetherRoomSettings
import com.ozyern.exhale.together.TogetherSessionState
import com.ozyern.exhale.ui.component.IconButton as AtIconButton
import com.ozyern.exhale.ui.component.TextFieldDialog
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTogetherScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val (welcomeShown, setWelcomeShown) = rememberPreference(TogetherWelcomeShownKey, false)
    var welcomeDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val showWelcome = !welcomeShown && !welcomeDismissedThisSession

    if (showWelcome) {
        TogetherWelcomeSheet(onContinue = {
            welcomeDismissedThisSession = true
            setWelcomeShown(true)
        })
    }

    val (displayName, setDisplayName) = rememberPreference(
        TogetherDisplayNameKey,
        defaultValue = Build.MODEL?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name),
    )
    val (port, setPort) = rememberPreference(TogetherDefaultPortKey, defaultValue = 42117)
    val (allowAddTracks, setAllowAddTracksRaw) = rememberPreference(
        TogetherAllowGuestsToAddTracksKey, defaultValue = true
    )
    val (allowControlPlayback, setAllowControlPlaybackRaw) = rememberPreference(
        TogetherAllowGuestsToControlPlaybackKey, defaultValue = false
    )
    val (requireApproval, setRequireApprovalRaw) = rememberPreference(
        TogetherRequireHostApprovalToJoinKey, defaultValue = false
    )
    val (lastJoinLink, setLastJoinLink) = rememberPreference(
        TogetherLastJoinLinkKey, defaultValue = ""
    )

    val sessionStateFlow = remember(playerConnection) {
        playerConnection?.service?.togetherSessionState ?: MutableStateFlow(TogetherSessionState.Idle)
    }
    val sessionState by sessionStateFlow.collectAsState()

    val isHosting = sessionState is TogetherSessionState.Hosting || sessionState is TogetherSessionState.HostingOnline
    val isJoining = sessionState is TogetherSessionState.Joining || sessionState is TogetherSessionState.JoiningOnline
    val isHostRole = when (val state = sessionState) {
        is TogetherSessionState.Hosting, is TogetherSessionState.HostingOnline -> true
        is TogetherSessionState.Joined  -> state.role is TogetherRole.Host
        else -> false
    }
    val isCreatingSessionLoading =
        ((sessionState as? TogetherSessionState.Hosting)?.roomState == null && sessionState is TogetherSessionState.Hosting) ||
            ((sessionState as? TogetherSessionState.HostingOnline)?.roomState == null && sessionState is TogetherSessionState.HostingOnline)
    val isJoinedAsGuest = (sessionState as? TogetherSessionState.Joined)?.role is TogetherRole.Guest
    val isWaitingApproval = run {
        val joined = sessionState as? TogetherSessionState.Joined ?: return@run false
        joined.role is TogetherRole.Guest &&
                joined.roomState.participants.firstOrNull { it.id == joined.selfParticipantId }?.isPending == true
    }
    val isJoinedAsAcceptedGuest = isJoinedAsGuest && !isWaitingApproval
    val disableJoinUi = isHostRole || isCreatingSessionLoading || isJoinedAsGuest

    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showPortDialog by rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by rememberSaveable { mutableStateOf(false) }

    val hostingLan = sessionState as? TogetherSessionState.Hosting
    val hostingOnline = sessionState as? TogetherSessionState.HostingOnline
    val lanParticipants = (hostingLan?.roomState ?: hostingOnline?.roomState)?.participants.orEmpty()
    var confirmKickParticipantId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmBanParticipantId  by rememberSaveable { mutableStateOf<String?>(null) }
    val confirmKickName = lanParticipants.firstOrNull { it.id == confirmKickParticipantId }?.name
    val confirmBanName  = lanParticipants.firstOrNull { it.id == confirmBanParticipantId  }?.name

    LaunchedEffect(disableJoinUi, isJoining, isHosting) {
        if (disableJoinUi || isJoining || isHosting) showJoinDialog = false
    }

    fun pushSettingsToActiveSession(
        addTracks: Boolean = allowAddTracks,
        controlPlayback: Boolean = allowControlPlayback,
        approval: Boolean = requireApproval,
    ) {
        if (isHosting) {
            playerConnection?.service?.updateTogetherSettings(
                TogetherRoomSettings(
                    allowGuestsToAddTracks = addTracks,
                    allowGuestsToControlPlayback = controlPlayback,
                    requireHostApprovalToJoin = approval,
                )
            )
        }
    }

    val setAllowAddTracks: (Boolean) -> Unit = { v ->
        setAllowAddTracksRaw(v); pushSettingsToActiveSession(addTracks = v)
    }
    val setAllowControlPlayback: (Boolean) -> Unit = { v ->
        setAllowControlPlaybackRaw(v); pushSettingsToActiveSession(controlPlayback = v)
    }
    val setRequireApproval: (Boolean) -> Unit = { v ->
        setRequireApprovalRaw(v); pushSettingsToActiveSession(approval = v)
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showNameDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.together_display_name)) },
            placeholder = { Text(stringResource(R.string.together_display_name_placeholder)) },
            isInputValid = { it.trim().isNotBlank() },
            onDone = { setDisplayName(it.trim()) },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showPortDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.together_port)) },
            placeholder = { Text("42117") },
            isInputValid = { it.trim().toIntOrNull() in 1..65535 },
            onDone = { setPort(it.trim().toInt()) },
            onDismiss = { showPortDialog = false },
        )
    }

    var joinInput by rememberSaveable { mutableStateOf(lastJoinLink) }
    val canJoin = remember(joinInput) { TogetherLink.decode(joinInput) != null }

    if (showJoinDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.join_session)) },
            placeholder = { Text(stringResource(R.string.together_join_link_hint)) },
            singleLine = false,
            maxLines = 8,
            isInputValid = { TogetherLink.decode(it) != null },
            onDone = { raw ->
                val trimmed = raw.trim()
                joinInput = trimmed
                setLastJoinLink(trimmed)
                playerConnection?.service?.joinTogether(trimmed, displayName)
            },
            onDismiss = { showJoinDialog = false },
        )
    }

    // Kick / Ban: iOS alerts, Cancel on the left and the red action on the right.
    if (confirmKickParticipantId != null) {
        IosAlert(
            title = stringResource(R.string.together_kick),
            message = stringResource(R.string.together_kick_confirm, confirmKickName ?: stringResource(R.string.unknown)),
            confirmText = stringResource(R.string.together_kick),
            destructive = true,
            onConfirm = {
                val pid = confirmKickParticipantId
                confirmKickParticipantId = null
                if (pid != null) playerConnection?.service?.kickTogetherParticipant(pid)
            },
            onDismiss = { confirmKickParticipantId = null },
        )
    }

    if (confirmBanParticipantId != null) {
        IosAlert(
            title = stringResource(R.string.together_ban),
            message = stringResource(R.string.together_ban_confirm, confirmBanName ?: stringResource(R.string.unknown)),
            confirmText = stringResource(R.string.together_ban),
            destructive = true,
            onConfirm = {
                val pid = confirmBanParticipantId
                confirmBanParticipantId = null
                if (pid != null) playerConnection?.service?.banTogetherParticipant(pid)
            },
            onDismiss = { confirmBanParticipantId = null },
        )
    }

    // ── Screen content ────────────────────────────────────────────────────────

    var mode by rememberSaveable { mutableStateOf(TogetherMode.Nearby) }
    var onlineCode by rememberSaveable { mutableStateOf("") }
    val idle = sessionState is TogetherSessionState.Idle || sessionState is TogetherSessionState.Error
    val hostSettings = hostingLan?.settings ?: hostingOnline?.settings

    val copy: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }
    val share: (String) -> Unit = { text ->
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                null,
            )
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            // Below the content, not around the viewport: the page scrolls on under the dock.
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        val joinedState = sessionState as? TogetherSessionState.Joined
        val roomPeople = when {
            isHostRole -> lanParticipants.filterNot { it.isPending && hostSettings?.requireHostApprovalToJoin == true }
            joinedState != null -> joinedState.roomState.participants.filterNot { it.isPending }
            else -> emptyList()
        }
        TogetherHeader(
            state = sessionState,
            listeners = roomPeople.count { !it.isHost },
            people = roomPeople.map { it.name },
            hostIndex = roomPeople.indexOfFirst { it.isHost }.takeIf { it >= 0 },
            onDismissError = { playerConnection?.service?.leaveTogether() },
        )

        // Starting, joining and leaving move the page between two layouts; they cross with a
        // fade and a short rise rather than one frame swapping for the other.
        androidx.compose.animation.AnimatedContent(
            targetState = idle,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(260, delayMillis = 60)) +
                    androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 14 }) togetherWith
                    (androidx.compose.animation.fadeOut(tween(140)) +
                        androidx.compose.animation.slideOutVertically(tween(180)) { -it / 24 }) using
                    androidx.compose.animation.SizeTransform(clip = false)
            },
            label = "togetherBody",
        ) { showIdle ->
        Column {
        if (showIdle) {
            ModeSwitch(mode = mode, onMode = { mode = it })

            CapsuleButton(
                text = if (mode == TogetherMode.Nearby) "Start Session" else "Start Online Session",
                icon = if (mode == TogetherMode.Nearby) R.drawable.wifi else R.drawable.language,
                onClick = {
                    val settings = TogetherRoomSettings(
                        allowGuestsToAddTracks = allowAddTracks,
                        allowGuestsToControlPlayback = allowControlPlayback,
                        requireHostApprovalToJoin = requireApproval,
                    )
                    if (mode == TogetherMode.Online) {
                        playerConnection?.service?.startTogetherOnlineHost(displayName = displayName, settings = settings)
                    } else {
                        playerConnection?.service?.startTogetherHost(port = port, displayName = displayName, settings = settings)
                    }
                },
                enabled = !isCreatingSessionLoading && !isJoining && !isHosting,
                loading = isCreatingSessionLoading,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp),
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = mode == TogetherMode.Nearby,
                enter = androidx.compose.animation.fadeIn(tween(220, delayMillis = 60)) + androidx.compose.animation.expandVertically(spring(dampingRatio = 0.9f, stiffness = 420f)),
                exit = androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)),
            ) {
                TogetherCard {
                    TogetherTip(
                        icon = R.drawable.wifi,
                        tint = Color(0xFF34C759),
                        title = "No Wi-Fi? Use a hotspot",
                        body = "Turn on your hotspot, have the other phone join it, then start the session. Two phones are all it takes. No router needed.",
                        modifier = Modifier.padding(16.dp),
                        action = {
                            Text(
                                "Open Hotspot Settings",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { openHotspotSettings(context) },
                            )
                        },
                    )
                }
            }

            val nearbyValid = canJoin
            val onlineValid = onlineCode.length >= 4
            JoinCard(
                mode = mode,
                input = if (mode == TogetherMode.Online) onlineCode else joinInput,
                onInput = { if (mode == TogetherMode.Online) onlineCode = it else joinInput = it },
                valid = if (mode == TogetherMode.Online) onlineValid else nearbyValid,
                enabled = !disableJoinUi && !isJoining,
                onPaste = {
                    val text = clipboard.getText()?.text?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (mode == TogetherMode.Online) onlineCode = text.uppercase().filter { it.isLetterOrDigit() }.take(8) else joinInput = text
                    }
                },
                onJoin = {
                    if (mode == TogetherMode.Online) {
                        playerConnection?.service?.joinTogetherOnline(onlineCode, displayName)
                    } else {
                        val trimmed = joinInput.trim()
                        setLastJoinLink(trimmed)
                        playerConnection?.service?.joinTogether(trimmed, displayName)
                    }
                },
            )

            TogetherSection("You", footer = "This is the name everyone else in the room sees.") {
                PuckRow(R.drawable.person, "Your name", subtitle = displayName, onClick = { showNameDialog = true })
            }

            RulesCard(
                allowAddTracks = allowAddTracks,
                allowControlPlayback = allowControlPlayback,
                requireApproval = requireApproval,
                onAllowAddTracks = setAllowAddTracks,
                onAllowControlPlayback = setAllowControlPlayback,
                onRequireApproval = setRequireApproval,
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = mode == TogetherMode.Online,
                enter = androidx.compose.animation.fadeIn(tween(220, delayMillis = 60)) + androidx.compose.animation.expandVertically(spring(dampingRatio = 0.9f, stiffness = 420f)),
                exit = androidx.compose.animation.fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)),
            ) {
                RelayCard()
            }
        } else {
            // A room is running (or being joined): the way in, who is in, and what they may do.
            if (hostingOnline != null) {
                InviteCode(code = hostingOnline.code, onCopy = copy, onShare = share)
            } else if (hostingLan != null) {
                InviteQr(
                    link = hostingLan.joinLink,
                    addressHint = hostingLan.localAddressHint?.let { "$it:${hostingLan.port}" },
                    onCopy = copy,
                    onShare = share,
                )
            }

            if (isHostRole && lanParticipants.isNotEmpty()) {
                OnlineParticipantsCard(
                    participants = lanParticipants,
                    hostApprovalEnabled = hostSettings?.requireHostApprovalToJoin == true,
                    onApprove = { pid, approved -> playerConnection?.service?.approveTogetherParticipant(pid, approved) },
                    onKick = { confirmKickParticipantId = it },
                    onBan = { confirmBanParticipantId = it },
                )
            }

            if (joinedState != null && joinedState.role is TogetherRole.Guest) {
                ParticipantsCard(joinedState.roomState.participants.map { it.name })
            }

            if (isHostRole) {
                RulesCard(
                    allowAddTracks = hostSettings?.allowGuestsToAddTracks ?: allowAddTracks,
                    allowControlPlayback = hostSettings?.allowGuestsToControlPlayback ?: allowControlPlayback,
                    requireApproval = hostSettings?.requireHostApprovalToJoin ?: requireApproval,
                    onAllowAddTracks = setAllowAddTracks,
                    onAllowControlPlayback = setAllowControlPlayback,
                    onRequireApproval = setRequireApproval,
                )
            }

            CapsuleButton(
                text = if (isHostRole) "End Session" else "Leave Session",
                icon = R.drawable.leave,
                destructive = true,
                filled = false,
                onClick = { playerConnection?.service?.leaveTogether() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 24.dp),
            )
        }
        }
        }

        Spacer(Modifier.height(24.dp))
    }

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.music_together)) },
        navigationIcon = {
            com.ozyern.exhale.ui.component.LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            )
        },
        scrollBehavior = scrollBehavior,
    )
}


// ── The screen's parts ───────────────────────────────────────────────────────
//
// Built from the same pieces as every other settings page — the frosted inset group, the quiet
// section header above it, the icon puck, hairline dividers between rows, a footer under it — so
// Together reads as part of the app rather than a screen that arrived from somewhere else. Actions
// are rows in the accent colour, the way a grouped list asks for them, not Material buttons laid
// on top of glass.

private enum class TogetherMode { Nearby, Online }

@Composable
private fun TogetherSection(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) PreferenceGroupTitle(title)
        Column(Modifier.fillMaxWidth().animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)), content = content)
        if (footer != null) {
            Text(
                footer,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

/** One glass card with the same margins and shape a settings entry has, for things that aren't one row. */
@Composable
private fun TogetherCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius))
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        content = content,
    )
}

@Composable
private fun GroupDivider(indent: androidx.compose.ui.unit.Dp = SettingsDimensions.DividerStartIndent) = HorizontalDivider(
    modifier = Modifier.padding(start = indent),
    thickness = SettingsDimensions.DividerThickness,
    color = SettingsDimensions.dividerColor(),
)

/** A pressed row fills with a neutral grey edge to edge and does not move, as on every other settings page. */
@Composable
private fun Modifier.rowPress(onClick: (() -> Unit)?): Modifier {
    if (onClick == null) return this
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val wash by animateFloatAsState(if (pressed) 0.09f else 0f, SettingsAnimations.pressSpring(), label = "togetherRowPress")
    return this
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
        .clickable(interactionSource = source, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
private fun PuckRow(
    icon: Int,
    title: String,
    subtitle: String? = null,
    accent: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    PreferenceEntry(
        title = { Text(title) },
        // Keyed by the row's own title so the puck is the same colour every time this page opens,
        // rather than changing with where the row happens to land in the tree.
        iconTintKey = title,
        description = subtitle,
        icon = { Icon(painterResource(icon), null) },
        trailingContent = trailing,
        onClick = onClick,
    )
}


/** An action as a grouped list writes one: a row whose label is the accent colour, centred. */
@Composable
private fun BareAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
    prominent: Boolean = false,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .rowPress(if (enabled && !loading) onClick else null)
            .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            LoadingRing(Modifier.size(20.dp), stroke = 2.dp, color = color)
        } else {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
            )
        }
    }
}

/**
 * The top of the page, centred the way SharePlay and AirDrop are: the orb, the state in a large
 * title, one line under it, and the faces of everyone in the room once there is a room.
 */
@Composable
private fun TogetherHeader(
    state: TogetherSessionState,
    listeners: Int,
    people: List<String>,
    hostIndex: Int?,
    onDismissError: () -> Unit,
) {
    val isError = state is TogetherSessionState.Error
    val busy = state is TogetherSessionState.Joining || state is TogetherSessionState.JoiningOnline
    val isActive = state !is TogetherSessionState.Idle && !isError
    val waiting = (state as? TogetherSessionState.Joined)?.let { joined ->
        joined.role is TogetherRole.Guest && joined.roomState.participants.firstOrNull { it.id == joined.selfParticipantId }?.isPending == true
    } == true
    val title = when (state) {
        TogetherSessionState.Idle -> "Music Together"
        is TogetherSessionState.Hosting, is TogetherSessionState.HostingOnline -> "You're Hosting"
        is TogetherSessionState.Joining, is TogetherSessionState.JoiningOnline -> "Joining…"
        is TogetherSessionState.Joined -> if (waiting) "Waiting for the Host" else "Listening Together"
        is TogetherSessionState.Error -> "That Didn't Work"
    }
    val subtitle = when (state) {
        TogetherSessionState.Idle -> "Every phone plays the same song, at the same second."
        is TogetherSessionState.Hosting, is TogetherSessionState.HostingOnline -> when {
            listeners <= 0 -> "Waiting for someone to join"
            listeners == 1 -> "1 person listening with you"
            else -> "$listeners people listening with you"
        }
        is TogetherSessionState.Joining, is TogetherSessionState.JoiningOnline -> "Connecting to the room"
        is TogetherSessionState.Joined -> if (waiting) "You'll hear the music the moment you're let in" else "In sync with the host"
        is TogetherSessionState.Error -> state.message
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TogetherOrb(active = isActive && !waiting, error = isError, busy = busy)
        // Title 1, the size iOS gives a sheet's subject. 30sp was a hair over every other heading
        // in the app, which made this page look like it came from somewhere else.
        // The state's name changes like a page turning: the old line lifts away, the new one rises.
        androidx.compose.animation.AnimatedContent(
            targetState = title,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(240, delayMillis = 70)) +
                    androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) { it / 2 }) togetherWith
                    (androidx.compose.animation.fadeOut(tween(130)) + androidx.compose.animation.slideOutVertically(tween(160)) { -it / 3 })
            },
            label = "togetherTitle",
        ) { text ->
            Text(
                text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = subtitle,
            transitionSpec = {
                androidx.compose.animation.fadeIn(tween(260, delayMillis = 110)) togetherWith androidx.compose.animation.fadeOut(tween(120))
            },
            label = "togetherSubtitle",
        ) { text ->
            Text(
                text,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        if (people.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            AvatarStack(people, hostIndex)
        }
        if (isError) {
            Spacer(Modifier.height(16.dp))
            CapsuleButton("Dismiss", onClick = onDismissError, filled = false, height = 40.dp)
        }
    }
}

/** Nearby or Online as a segmented control: a thumb that slides, not two buttons that light up. */
@Composable
private fun ModeSwitch(mode: TogetherMode, onMode: (TogetherMode) -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth()) {
        PreferenceGroupTitle("Where your friends are")
        PreferenceGroup {
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.10f else 0.07f))
                    .padding(3.dp),
            ) {
                val half = maxWidth / 2
                val offset by animateDpAsState(
                    if (mode == TogetherMode.Nearby) 0.dp else half,
                    spring(dampingRatio = 0.78f, stiffness = 520f),
                    label = "togetherModeThumb",
                )
                Box(
                    Modifier
                        .offset(x = offset)
                        .width(half)
                        .fillMaxHeight()
                        .shadow(if (dark) 0.dp else 2.dp, RoundedCornerShape(9.dp), clip = false)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (dark) Color.White.copy(alpha = 0.20f) else Color.White),
                )
                Row(Modifier.fillMaxSize()) {
                    for ((value, label) in listOf(TogetherMode.Nearby to "Nearby", TogetherMode.Online to "Online")) {
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    if (mode != value) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onMode(value)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (mode == value) FontWeight.SemiBold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            androidx.compose.animation.Crossfade(targetState = mode, animationSpec = tween(220), label = "togetherModeText") { m ->
                Text(
                    if (m == TogetherMode.Nearby) "Phones on the same Wi-Fi. No server, nothing leaves the network."
                    else "Friends anywhere join with a short code, through a relay server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp),
                )
            }
        }

    }
}

@Composable
private fun RulesCard(
    allowAddTracks: Boolean,
    allowControlPlayback: Boolean,
    requireApproval: Boolean,
    onAllowAddTracks: (Boolean) -> Unit,
    onAllowControlPlayback: (Boolean) -> Unit,
    onRequireApproval: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TogetherSection("Guests", modifier, footer = "Changes reach everyone in the room straight away.") {
        SwitchPreference(
            title = { Text("Can add songs") },
            icon = { Icon(painterResource(R.drawable.playlist_add), null) },
            checked = allowAddTracks,
            onCheckedChange = onAllowAddTracks,
        )
        SwitchPreference(
            title = { Text("Can play, pause and skip") },
            icon = { Icon(painterResource(R.drawable.play), null) },
            checked = allowControlPlayback,
            onCheckedChange = onAllowControlPlayback,
        )
        SwitchPreference(
            title = { Text("Ask before letting someone in") },
            icon = { Icon(painterResource(R.drawable.lock), null) },
            checked = requireApproval,
            onCheckedChange = onRequireApproval,
        )
    }
}

@Composable
private fun JoinCard(
    mode: TogetherMode,
    input: String,
    onInput: (String) -> Unit,
    valid: Boolean,
    enabled: Boolean,
    onPaste: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val online = mode == TogetherMode.Online
    TogetherSection(
        "Join",
        modifier,
        footer = if (online) "Ask the host for their room code." else "Scan the host's QR code with your camera, or paste their invite link.",
    ) {
        TogetherCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = if (online) 18.dp else 14.dp),
                contentAlignment = if (online) Alignment.Center else Alignment.CenterStart,
            ) {
                val style = if (online) {
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace)
                }
                if (input.isEmpty()) {
                    Text(
                        if (online) "ABC123" else "Exhale://together?…",
                        style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                        modifier = if (online) Modifier.fillMaxWidth() else Modifier,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = input,
                    onValueChange = { raw -> onInput(if (online) raw.uppercase().filter { it.isLetterOrDigit() }.take(8) else raw) },
                    enabled = enabled,
                    singleLine = online,
                    maxLines = if (online) 1 else 4,
                    textStyle = style,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = if (online) androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                        else androidx.compose.ui.text.input.KeyboardCapitalization.None,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp)) {
            CapsuleButton("Paste", onClick = onPaste, filled = false, enabled = enabled, height = 46.dp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            CapsuleButton("Join", onClick = onJoin, enabled = enabled && valid, height = 46.dp, modifier = Modifier.weight(1f))
        }
    }
}

/** Which relay Online uses, and whether it answers — so a dead server is seen here, not guessed at. */
@Composable
private fun RelayCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val (custom, setCustom) = rememberPreference(TogetherRelayUrlKey, defaultValue = "")
    var editing by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var healthy by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var testing by rememberSaveable { mutableStateOf(false) }

    if (editing) {
        TextFieldDialog(
            title = { Text("Relay server") },
            placeholder = { Text("relay.example.com — blank for the shared one") },
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(custom),
            isInputValid = { it.isBlank() || TogetherOnlineEndpoint.normalizedRelayUrlOrNull(it) != null },
            onDone = {
                setCustom(it.trim())
                result = null
                healthy = null
            },
            onDismiss = { editing = false },
        )
    }

    TogetherSection(
        "Relay server",
        modifier,
        footer = "Online rooms meet on a small server that relays timing, never music. Run your own from the together-relay folder.",
    ) {
        PuckRow(
            icon = R.drawable.language,
            title = if (custom.isBlank()) "Shared relay" else custom,
            subtitle = result ?: "Tap to use your own",
            accent = when (healthy) {
                true -> Color(0xFF34C759)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.primary
            },
            onClick = { editing = true },
        )
        ActionRow(
            text = "Test connection",
            icon = R.drawable.sync,
            loading = testing,
            onClick = {
                testing = true
                result = null
                scope.launch {
                    val (ok, message) = withContext(Dispatchers.IO) { testRelay(context, custom) }
                    healthy = ok
                    result = message
                    testing = false
                }
            },
        )
    }
}

/** Asks the relay's own health address, and says in words what came back. */
private suspend fun testRelay(context: android.content.Context, custom: String): Pair<Boolean, String> {
    val base = TogetherOnlineEndpoint.normalizedRelayUrlOrNull(custom)
        ?: TogetherOnlineEndpoint.baseUrlOrNull(context.dataStore)
        ?: return false to "No relay is configured"
    return runCatching {
        val connection = java.net.URL(base.trimEnd('/') + "/health").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        val code = connection.responseCode
        connection.disconnect()
        if (code in 200..299) true to "Working" else false to "Not responding (HTTP $code)"
    }.getOrElse { false to "Can't be reached (${it.javaClass.simpleName})" }
}

/**
 * The way in on a nearby session: the invite as a QR code. The other phone's camera reads it and
 * opens Exhale straight into the room, which is also the easy way to pair over a hotspot.
 */
@Composable
private fun InviteQr(link: String, addressHint: String?, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TogetherQrCard(link)
        Spacer(Modifier.height(14.dp))
        Text(
            "Scan with the other phone's camera",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            addressHint?.let { "Both phones on the same Wi-Fi or hotspot · $it" } ?: "Both phones on the same Wi-Fi or hotspot",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 2.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)) {
            CapsuleButton("Copy Link", onClick = { onCopy(link) }, icon = R.drawable.link, filled = false, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            CapsuleButton("Share", onClick = { onShare(link) }, icon = R.drawable.share, modifier = Modifier.weight(1f))
        }
    }
}

/** The way in on an online session: the room code, large, with the same two buttons. */
@Composable
private fun InviteCode(code: String, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TogetherCard {
            Text(
                code.chunked(3).joinToString(" "),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().rowPress { onCopy(code) }.padding(vertical = 26.dp),
            )
        }
        Text(
            "Anyone with this code can ask to join.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)) {
            CapsuleButton("Copy Code", onClick = { onCopy(code) }, filled = false, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            CapsuleButton("Share", onClick = { onShare("Join my Exhale session with the code $code") }, icon = R.drawable.share, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallRoundAction(icon: Int, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun OnlineParticipantsCard(
    participants: List<com.ozyern.exhale.together.TogetherParticipant>,
    hostApprovalEnabled: Boolean,
    onApprove: (participantId: String, approved: Boolean) -> Unit,
    onKick: (participantId: String) -> Unit,
    onBan: (participantId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val waiting = participants.filter { it.isPending && hostApprovalEnabled }
    val inRoom = participants.filterNot { it.isPending && hostApprovalEnabled }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SectionSpacing)) {
        if (waiting.isNotEmpty()) {
            TogetherSection("Waiting to join") {
                waiting.forEachIndexed { index, person ->
                    key(person.id) {
                        if (index > 0) GroupDivider()
                        PreferenceEntry(
                            title = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            description = "Wants to join",
                            icon = { TogetherAvatar(person.name, host = false) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SmallRoundAction(R.drawable.close, "Decline", MaterialTheme.colorScheme.error) { onApprove(person.id, false) }
                                    Spacer(Modifier.width(8.dp))
                                    SmallRoundAction(R.drawable.check, "Let in", Color(0xFF34C759)) { onApprove(person.id, true) }
                                }
                            },
                        )
                    }
                }
            }
        }
        TogetherSection(if (inRoom.size == 1) "In the room" else "In the room · ${inRoom.size}") {
            inRoom.forEachIndexed { index, person ->
                key(person.id) {
                    if (index > 0) GroupDivider()
                    PreferenceEntry(
                        title = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        description = if (person.isHost) "Host" else "Listening",
                        icon = { TogetherAvatar(person.name, host = person.isHost) },
                        trailingContent = if (person.isHost) {
                            null
                        } else {
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SmallRoundAction(R.drawable.kick, "Remove", MaterialTheme.colorScheme.onSurfaceVariant) { onKick(person.id) }
                                    Spacer(Modifier.width(8.dp))
                                    SmallRoundAction(R.drawable.block, "Ban", MaterialTheme.colorScheme.error) { onBan(person.id) }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantsCard(participants: List<String>) {
    TogetherSection(if (participants.size == 1) "In the room" else "In the room · ${participants.size}") {
        participants.forEachIndexed { index, name ->
            if (index > 0) GroupDivider()
            PreferenceEntry(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                description = if (index == 0) "Host" else null,
                icon = { TogetherAvatar(name, host = index == 0) },
            )
        }
    }
}

/** A standalone action, as its own settings card: the label in the accent colour (red when destructive). */
@Composable
private fun ActionRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
    prominent: Boolean = false,
    icon: Int? = null,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    PreferenceEntry(
        modifier = modifier,
        title = {
            Text(text, color = color, fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Medium)
        },
        icon = icon?.let { res -> { Icon(painterResource(res), null, tint = color) } },
        trailingContent = if (loading) {
            { LoadingRing(Modifier.size(20.dp), stroke = 2.dp, color = color) }
        } else {
            null
        },
        onClick = if (enabled && !loading) onClick else null,
        isEnabled = enabled,
    )
}
