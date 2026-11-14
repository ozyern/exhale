/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import android.content.Context
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.ui.utils.combinedBounceClick
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.ios.compat.Button
import androidx.compose.material3.ButtonDefaults
import com.ozyern.exhale.ui.ios.compat.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.ozyern.exhale.ui.ios.compat.SegmentedButton
import com.ozyern.exhale.ui.ios.compat.SegmentedButtonDefaults
import com.ozyern.exhale.ui.ios.compat.SingleChoiceSegmentedButtonRow
import com.ozyern.exhale.ui.ios.compat.Surface
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import com.ozyern.exhale.ui.ios.compat.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ozyern.exhale.LocalListenTogetherManager
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFBubbleLeftAndBubbleRight
import com.composables.sfsymbols.monochrome.SFCheckmark
import com.composables.sfsymbols.monochrome.SFChevronRight
import com.composables.sfsymbols.monochrome.SFCrown
import com.composables.sfsymbols.monochrome.SFGearshape
import com.composables.sfsymbols.monochrome.SFLink
import com.composables.sfsymbols.monochrome.SFListBullet
import com.composables.sfsymbols.monochrome.SFPerson2
import com.composables.sfsymbols.monochrome.SFRectanglePortraitAndArrowRight
import com.composables.sfsymbols.monochrome.SFSquareOnSquare
import com.composables.sfsymbols.monochrome.SFStar
import com.composables.sfsymbols.monochrome.SFTimer
import com.composables.sfsymbols.monochrome.SFXmark
import androidx.compose.ui.graphics.vector.ImageVector
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.constants.ListenTogetherInTopBarKey
import com.ozyern.exhale.constants.ListenTogetherUsernameKey
import com.ozyern.exhale.listentogether.ConnectionState
import com.ozyern.exhale.listentogether.JoinRequestPayload
import com.ozyern.exhale.listentogether.ListenTogetherEvent
import com.ozyern.exhale.listentogether.MAX_ROOM_CODE_LENGTH
import com.ozyern.exhale.listentogether.MIN_ROOM_CODE_LENGTH
import com.ozyern.exhale.listentogether.SuggestionReceivedPayload
import com.ozyern.exhale.listentogether.UserInfo
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.launch
import com.ozyern.exhale.ui.component.ScreenScaffold
import androidx.compose.ui.platform.LocalLocale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListenTogetherScreen(
    navController: NavController,
    showTopBar: Boolean = false
) {
    val context = LocalContext.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    if (listenTogetherManager == null) {
        NotConfiguredContent()
        return
    }

    val connectionState by listenTogetherManager.connectionState.collectAsStateWithLifecycle()
    val roomState by listenTogetherManager.roomState.collectAsStateWithLifecycle()
    val userId by listenTogetherManager.userId.collectAsStateWithLifecycle()
    val pendingJoinRequests by listenTogetherManager.pendingJoinRequests.collectAsStateWithLifecycle()
    val pendingSuggestions by listenTogetherManager.pendingSuggestions.collectAsStateWithLifecycle()
    val controlMode by listenTogetherManager.controlMode.collectAsStateWithLifecycle()
    val roomExpiresAt by listenTogetherManager.roomExpiresAt.collectAsStateWithLifecycle()
    val roomExpiring by listenTogetherManager.roomExpiring.collectAsStateWithLifecycle()
    val extensionsLeft by listenTogetherManager.extensionsLeft.collectAsStateWithLifecycle()

    val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
    val shouldShowTopBar = showTopBar || listenTogetherInTopBar
    
    var savedUsername by rememberPreference(ListenTogetherUsernameKey, "")
    var roomCodeInput by rememberSaveable { mutableStateOf("") }
    var usernameInput by rememberSaveable { mutableStateOf(savedUsername) }

    var isCreatingRoom by rememberSaveable { mutableStateOf(false) }
    var isJoiningRoom by rememberSaveable { mutableStateOf(false) }
    var joinErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var selectedUserForMenu by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUsername by rememberSaveable { mutableStateOf<String?>(null) }

    val waitingForApprovalText = stringResource(R.string.waiting_for_approval)
    val invalidRoomCodeText = stringResource(R.string.invalid_room_code)
    val joinRequestDeniedText = stringResource(R.string.join_request_denied)

    LaunchedEffect(savedUsername) {
        if (usernameInput.isBlank() && savedUsername.isNotBlank()) {
            usernameInput = savedUsername
        }
    }

    LaunchedEffect(listenTogetherManager) {
        listenTogetherManager.events.collect { event ->
            when (event) {
                is ListenTogetherEvent.JoinRejected -> {
                    val reason = event.reason
                    joinErrorMessage = when {
                        reason.isNullOrBlank() -> joinRequestDeniedText
                        reason.contains("invalid", ignoreCase = true) -> invalidRoomCodeText
                        else -> "$joinRequestDeniedText: $reason"
                    }
                    isJoiningRoom = false
                    isCreatingRoom = false
                }
                is ListenTogetherEvent.JoinApproved -> {
                    isJoiningRoom = false
                    joinErrorMessage = null
                }
                is ListenTogetherEvent.RoomCreated -> {
                    isCreatingRoom = false
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("ListenTogetherRoom", event.roomCode)
                    clipboard.setPrimaryClip(clip)
                }
                else -> {}
            }
        }
    }

    val isInRoom = listenTogetherManager.isInRoom
    val isHost = roomState?.hostId == userId
    if (selectedUserForMenu != null && selectedUsername != null) {
        UserActionDialog(
            username = selectedUsername ?: "",
            onKick = {
                selectedUserForMenu?.let {
                    listenTogetherManager.kickUser(it, "Removed by host")
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onPermanentKick = {
                selectedUserForMenu?.let { userId ->
                    selectedUsername?.let { username ->
                        listenTogetherManager.blockUser(username)
                        listenTogetherManager.kickUser(userId, R.string.user_blocked_by_host.toString())
                    }
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onTransferOwnership = {
                selectedUserForMenu?.let {
                    listenTogetherManager.transferHost(it)
                }
                selectedUserForMenu = null
                selectedUsername = null
            },
            onDismiss = {
                selectedUserForMenu = null
                selectedUsername = null
            }
        )
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()
    
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.together),
        // Shown as a pushed page it needs the bar; as a tab it is one of the
        // top-level screens and carries no chrome at all.
        titleAlpha = if (shouldShowTopBar) 1f else 0f,
        onBack = if (shouldShowTopBar) { { navController.navigateUp() } } else null,
        onBackLongClick = if (shouldShowTopBar) { { navController.backToMain() } } else null,
    ) { chrome ->
    LazyColumn(
        state = lazyListState,
        modifier = chrome.contentModifier()
            .fillMaxSize()
            .background(AppTheme.colorScheme.background)
            .imePadding(),
        contentPadding = PaddingValues(
            // Include the side-panel inset so content clears the floating side bar
            // in tab view instead of sitting under it.
            start = windowInsets.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current) + 16.dp,
            end = windowInsets.asPaddingValues().calculateEndPadding(LocalLayoutDirection.current) + 16.dp,
            top = windowInsets.asPaddingValues().calculateTopPadding() + 16.dp,
            bottom = windowInsets.asPaddingValues().calculateBottomPadding() + 16.dp + AppBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderSection(isInRoom = isInRoom)
        }

        // Party mode: a code on a BitChord server, no account or room setup.
        item {
            androidx.compose.material3.Surface(
                onClick = { navController.navigate("listen_together/party") },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                color = AppTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.jam_title), style = AppTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.jam_desc),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    com.ozyern.exhale.ui.component.SymbolIcon(
                        symbol = com.ozyern.exhale.ui.component.sf("chevron.right"),
                        contentDescription = null, size = 16.dp,
                    )
                }
            }
        }

        item {
            ConnectionStatusCard(
                connectionState = connectionState,
                onConnect = { listenTogetherManager.connect() },
                onDisconnect = { listenTogetherManager.disconnect() },
                onReconnect = { listenTogetherManager.forceReconnect() }
            )
        }

        if (connectionState == ConnectionState.CONNECTED && !isInRoom) {
            item {
                Text(
                    text = stringResource(R.string.listen_together_background_disconnect_note),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isInRoom) {
            roomState?.let { room ->
                item {
                    RoomStatusCard(
                        roomCode = room.roomCode,
                        isHost = isHost,
                        context = context,
                        navController = navController
                    )
                }

                // Show the expiry before the server closes the room.
                if (roomExpiresAt > 0L) {
                    item {
                        SessionCountdownCard(
                            expiresAt = roomExpiresAt,
                            expiring = roomExpiring,
                            isHost = isHost,
                            extensionsLeft = extensionsLeft,
                            onExtend = { listenTogetherManager.extendSession() }
                        )
                    }
                }

                // The server remains authoritative for playback permissions.
                if (isHost) {
                    item {
                        ControlModeCard(
                            controlMode = controlMode,
                            onChange = { listenTogetherManager.setControlMode(it) }
                        )
                    }
                }

                val connectedUsers = room.users.filter { it.isConnected }
                val currentUserIdValue = userId ?: ""
                item {
                    ConnectedUsersSection(
                        users = connectedUsers,
                        isHost = isHost,
                        currentUserId = currentUserIdValue,
                        onUserClick = { clickedUserId, username ->
                            if (isHost && clickedUserId != currentUserIdValue) {
                                selectedUserForMenu = clickedUserId
                                selectedUsername = username
                            }
                        }
                    )
                }

                if (isHost && pendingJoinRequests.isNotEmpty()) {
                    item {
                        PendingJoinRequestsSection(
                            requests = pendingJoinRequests,
                            onApprove = { listenTogetherManager.approveJoin(it) },
                            onReject = { listenTogetherManager.rejectJoin(it, "Rejected by host") }
                        )
                    }
                }

                if (isHost && pendingSuggestions.isNotEmpty()) {
                    item {
                        PendingSuggestionsSection(
                            suggestions = pendingSuggestions,
                            onApprove = { listenTogetherManager.approveSuggestion(it) },
                            onReject = { listenTogetherManager.rejectSuggestion(it, "Rejected by host") }
                        )
                    }
                }

                // Keep the destructive action quiet while retaining its error color.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ContinuousRoundedRectangle(percent = 50))
                            .background(AppTheme.colorScheme.surfaceContainerHigh)
                            .bounceClick(onClick = { listenTogetherManager.leaveRoom() })
                            .padding(vertical = 15.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SymbolIcon(symbol = sf("rectangle.portrait.and.arrow.right"), contentDescription = null, size = 18.dp, tint = AppTheme.colorScheme.error)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = stringResource(R.string.leave_room),
                            style = AppTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            item {
                JoinCreateRoomSection(
                    usernameInput = usernameInput,
                    onUsernameChange = { usernameInput = it },
                    roomCodeInput = roomCodeInput,
                    onRoomCodeChange = { roomCodeInput = it },
                    savedUsername = savedUsername,
                    isJoiningRoom = isJoiningRoom,
                    joinErrorMessage = joinErrorMessage,
                    waitingForApprovalText = waitingForApprovalText,
                    bringIntoViewRequester = bringIntoViewRequester,
                    onCreateRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            Toast.makeText(context, R.string.creating_room, Toast.LENGTH_SHORT).show()
                            isCreatingRoom = true
                            isJoiningRoom = false
                            joinErrorMessage = null
                            // createRoom allocates the code before opening its socket.
                            listenTogetherManager.createRoom(finalUsername)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onJoinRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            Toast.makeText(
                                context,
                                context.getString(R.string.joining_room, roomCodeInput),
                                Toast.LENGTH_SHORT
                            ).show()
                            isJoiningRoom = true
                            isCreatingRoom = false
                            joinErrorMessage = null
                            listenTogetherManager.joinRoom(roomCodeInput, finalUsername)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFieldFocused = {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                )
            }
        }

        item {
            SettingsLinkCard(
                onClick = { navController.navigate("settings/integrations/listen_together") }
            )
        }
    }

    }
}

@Composable
private fun NotConfiguredContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            SymbolIcon(symbol = sf("person.2.fill"), contentDescription = null, size = 64.dp, tint = AppTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.listen_together),
                style = AppTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.listen_together_not_configured),
                style = AppTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = AppTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeaderSection(isInRoom: Boolean = false) {
    if (isInRoom) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.listen_together),
            style = AppTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = AppTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.listen_together_description),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit
) {
    // Keep connection status quiet unless the user needs to act.
    val broken = connectionState == ConnectionState.ERROR ||
        connectionState == ConnectionState.DISCONNECTED
    val busy = connectionState == ConnectionState.CONNECTING ||
        connectionState == ConnectionState.RECONNECTING

    val tone = when (connectionState) {
        ConnectionState.CONNECTED -> AppTheme.colorScheme.primary
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> AppTheme.colorScheme.tertiary
        ConnectionState.ERROR -> AppTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> AppTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 2.dp,
                color = tone
            )
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tone)
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = when (connectionState) {
                ConnectionState.CONNECTED -> stringResource(R.string.listen_together_connected)
                ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting)
                ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting)
                ConnectionState.ERROR -> stringResource(R.string.listen_together_error)
                ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_disconnected)
            },
            style = AppTheme.typography.bodySmall,
            color = tone,
            modifier = Modifier.weight(1f)
        )
        if (broken) {
            Text(
                text = stringResource(R.string.connect),
                style = AppTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .bounceClick(onClick = onConnect)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RoomStatusCard(
    roomCode: String,
    isHost: Boolean,
    context: Context,
    navController: NavController
) {
    val accent = AppTheme.colorScheme.primary
    val copiedText = stringResource(R.string.copied_to_clipboard)

    fun copy(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f))
                )
            )
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (isHost) {
                    stringResource(R.string.listen_together_you_are_host)
                } else {
                    stringResource(R.string.listen_together_you_are_guest)
                },
                style = AppTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(16.dp))

        // Tracking keeps room codes easy to scan when typing.
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.combinedBounceClick(
                onClick = { copy("Room Code", roomCode) },
                onLongClick = { copy("Room Code", roomCode) }
            )
        ) {
            roomCode.forEach { ch ->
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppTheme.colorScheme.surface.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ch.toString(),
                        style = AppTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.listen_together_tap_code_to_copy),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ListenTogetherRoomAction(
                icon = R.drawable.chat_msg,
                label = stringResource(R.string.comments),
                onClick = { navController.navigate("listen_together/chat") }
            )
            ListenTogetherRoomAction(
                icon = R.drawable.content_copy,
                label = stringResource(R.string.copy_code),
                onClick = { copy("Room Code", roomCode) }
            )
            if (isHost) {
                ListenTogetherRoomAction(
                    icon = R.drawable.link,
                    label = stringResource(R.string.copy_link),
                    onClick = {
                        // Share an app deep link for this room code.
                        copy("Listen Together Link", "vivimusic://listen?code=$roomCode")
                    }
                )
            }
        }
    }
}

/** Action tile used in the room header. */
@Composable
private fun ListenTogetherRoomAction(icon: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colorScheme.surface.copy(alpha = 0.45f))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = label,
            modifier = Modifier.size(19.dp),
            tint = AppTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = AppTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectedUsersSection(
    users: List<UserInfo>,
    isHost: Boolean,
    currentUserId: String,
    onUserClick: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.listen_together_in_the_room))
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy((-10).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            users.take(8).forEach { user ->
                MemberAvatar(
                    username = user.username,
                    isHost = user.userId == currentUserId && isHost ||
                        user.isHost,
                    isSelf = user.userId == currentUserId,
                    onClick = { onUserClick(user.userId, user.username) }
                )
            }
            if (users.size > 8) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${users.size - 8}",
                        style = AppTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = users.joinToString(", ") { it.username },
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (isHost && users.size > 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.listen_together_tap_member_hint),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/** Vertical spacing between room sections. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = AppTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = AppTheme.colorScheme.onSurfaceVariant
    )
}

/** Stable colors derived from each member's name. */
private val MemberColors = listOf(
    Color(0xFFFF2D55), Color(0xFF5856D6), Color(0xFF32ADE6),
    Color(0xFF34C759), Color(0xFFFF9F0A), Color(0xFFAF52DE),
    Color(0xFF64D2FF), Color(0xFFFF6482),
)

private fun colorFor(username: String): Color =
    MemberColors[(username.hashCode().let { if (it < 0) -it else it }) % MemberColors.size]

@Composable
private fun MemberAvatar(
    username: String,
    isHost: Boolean,
    isSelf: Boolean,
    onClick: () -> Unit
) {
    val tint = remember(username) { colorFor(username) }
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(46.dp)
                // A filled ring lets overlapping avatars blend into the surface.
                .clip(CircleShape)
                .background(AppTheme.colorScheme.background)
                .padding(2.dp)
                .clip(CircleShape)
                .background(tint)
                .bounceClick(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = username.take(1).uppercase(),
                style = AppTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        if (isHost) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                SymbolIcon(symbol = sf("star.fill"), contentDescription = null, size = 11.dp, tint = AppTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PendingJoinRequestsSection(
    requests: List<JoinRequestPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    // Keep pending requests visually quieter than connected users.
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.listen_together_join_requests))
        Spacer(Modifier.height(12.dp))

        requests.forEach { request ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                MemberAvatar(
                    username = request.username,
                    isHost = false,
                    isSelf = false,
                    onClick = {}
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = request.username,
                    style = AppTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ListenTogetherRowAction(
                    icon = R.drawable.close,
                    tint = AppTheme.colorScheme.onSurfaceVariant,
                    description = stringResource(R.string.reject),
                    onClick = { onReject(request.userId) }
                )
                Spacer(Modifier.width(6.dp))
                ListenTogetherRowAction(
                    icon = R.drawable.check,
                    tint = AppTheme.colorScheme.primary,
                    description = stringResource(R.string.approve),
                    onClick = { onApprove(request.userId) }
                )
            }
        }
    }
}

/** Circular action button aligned with the member avatars. */
@Composable
private fun ListenTogetherRowAction(
    icon: Int,
    tint: Color,
    description: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(AppTheme.colorScheme.surfaceContainerHighest)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun PendingSuggestionsSection(
    suggestions: List<SuggestionReceivedPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.pending_suggestions))
        Spacer(Modifier.height(12.dp))

        suggestions.forEach { suggestion ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                // Square artwork distinguishes track suggestions from member avatars.
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    SymbolIcon(symbol = sf("music.note.list"), contentDescription = null, size = 20.dp, tint = AppTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggestion.trackInfo.title,
                        style = AppTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.listen_together_suggested_by,
                            suggestion.fromUsername
                        ),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ListenTogetherRowAction(
                    icon = R.drawable.close,
                    tint = AppTheme.colorScheme.onSurfaceVariant,
                    description = stringResource(R.string.reject),
                    onClick = { onReject(suggestion.suggestionId) }
                )
                Spacer(Modifier.width(6.dp))
                ListenTogetherRowAction(
                    icon = R.drawable.check,
                    tint = AppTheme.colorScheme.primary,
                    description = stringResource(R.string.approve),
                    onClick = { onApprove(suggestion.suggestionId) }
                )
            }
        }
    }
}

@Composable
private fun JoinCreateRoomSection(
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    savedUsername: String,
    isJoiningRoom: Boolean,
    joinErrorMessage: String?,
    waitingForApprovalText: String,
    bringIntoViewRequester: BringIntoViewRequester,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onFieldFocused: () -> Unit = {}
) {
    val canAct = usernameInput.trim().isNotBlank() || savedUsername.isNotBlank()
    val hasCode = roomCodeInput.trim().length >= MIN_ROOM_CODE_LENGTH

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.listen_together_your_name))
        Spacer(Modifier.height(10.dp))

        EntryField(
            value = usernameInput,
            onValueChange = onUsernameChange,
            placeholder = stringResource(R.string.enter_username),
            onFieldFocused = onFieldFocused,
            trailing = {
                if (usernameInput.isNotBlank()) {
                    ListenTogetherRowAction(
                        icon = R.drawable.close,
                        tint = AppTheme.colorScheme.onSurfaceVariant,
                        description = null,
                        onClick = { onUsernameChange("") }
                    )
                }
            }
        )

        Spacer(Modifier.height(24.dp))

        PrimaryAction(
            label = stringResource(R.string.create_room),
            enabled = canAct,
            onClick = onCreateRoom
        )

        Spacer(Modifier.height(28.dp))
        DividerWithLabel(stringResource(R.string.listen_together_or_join))
        Spacer(Modifier.height(20.dp))

        EntryField(
            value = roomCodeInput,
            onValueChange = { onRoomCodeChange(it.uppercase().filter { c -> c.isLetterOrDigit() }.take(MAX_ROOM_CODE_LENGTH)) },
            placeholder = stringResource(R.string.enter_room_code),
            onFieldFocused = onFieldFocused,
            // Space characters so the room code is easy to distinguish.
            letterSpacing = 4.sp,
            bold = true,
            modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)
        )

        Spacer(Modifier.height(14.dp))

        SecondaryAction(
            label = if (isJoiningRoom) waitingForApprovalText else stringResource(R.string.join_room),
            enabled = canAct && hasCode && !isJoiningRoom,
            loading = isJoiningRoom,
            onClick = onJoinRoom
        )

        if (joinErrorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = joinErrorMessage,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colorScheme.error
            )
        }
    }
}

/** Filled text field for room entry. */
@Composable
private fun EntryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onFieldFocused: () -> Unit,
    modifier: Modifier = Modifier,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    bold: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                letterSpacing = letterSpacing,
                color = AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        textStyle = AppTheme.typography.bodyLarge.copy(
            letterSpacing = letterSpacing,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        ),
        trailingIcon = trailing,
        singleLine = true,
        shape = ContinuousRoundedRectangle(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = AppTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = AppTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFieldFocused() }
    )
}

@Composable
private fun PrimaryAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) AppTheme.colorScheme.primary
             else AppTheme.colorScheme.surfaceContainerHigh
    val fg = if (enabled) AppTheme.colorScheme.onPrimary
             else AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(percent = 50))
            .background(bg)
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val fg = if (enabled || loading) AppTheme.colorScheme.onSurface
             else AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(percent = 50))
            .background(AppTheme.colorScheme.surfaceContainerHigh)
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                strokeWidth = 2.dp,
                color = AppTheme.colorScheme.primary
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = AppTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
    }
}

@Composable
private fun DividerWithLabel(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
        )
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = AppTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
        )
    }
}

@Composable
private fun SettingsLinkCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .bounceClick(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SymbolIcon(symbol = sf("gearshape.fill"), contentDescription = null, size = 20.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.settings),
            style = AppTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        SymbolIcon(symbol = sf("arrow.right"), contentDescription = null, size = 16.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UserActionDialog(
    username: String,
    onKick: () -> Unit,
    onPermanentKick: () -> Unit,
    onTransferOwnership: () -> Unit,
    onDismiss: () -> Unit
) {
    DefaultDialog(
        onDismiss = onDismiss,
        icon = {
            SymbolIcon(symbol = sf("person.2.fill"), contentDescription = null, size = 28.dp)
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.manage_user),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = username,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(onClick = onKick),
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colorScheme.errorContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    SymbolIcon(symbol = sf("xmark"), contentDescription = null, size = 24.dp, tint = AppTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.kick_user),
                            style = AppTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.kick_user_desc),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(onClick = onPermanentKick),
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    SymbolIcon(symbol = sf("xmark"), contentDescription = null, size = 24.dp, tint = AppTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permanently_kick_user),
                            style = AppTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.permanently_kick_user_desc),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(onClick = onTransferOwnership),
                shape = RoundedCornerShape(12.dp),
                color = AppTheme.colorScheme.primaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.crown),
                        contentDescription = null,
                        tint = AppTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.transfer_ownership),
                            style = AppTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.transfer_ownership_desc),
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Shows how long the temporary room remains active. */
@Composable
private fun SessionCountdownCard(
    expiresAt: Long,
    expiring: Boolean,
    isHost: Boolean,
    extensionsLeft: Int,
    onExtend: () -> Unit
) {
    // Recomputed once a second rather than derived from a frame clock, so an
    // idle room is not redrawing at display rate just to tick a label.
    var remaining by remember(expiresAt) { mutableStateOf(expiresAt - System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            remaining = expiresAt - System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val totalSeconds = (remaining / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val pretty = if (hours > 0) {
        String.format(LocalLocale.current.platformLocale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(LocalLocale.current.platformLocale, "%d:%02d", minutes, seconds)
    }

    // Highlight the countdown only when the room is close to expiry.
    val tone = if (expiring) {
        AppTheme.colorScheme.error
    } else {
        AppTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SymbolIcon(symbol = sf("timer"), contentDescription = null, size = 15.dp, tint = tone)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (expiring) R.string.listen_together_session_ending_soon
                else R.string.listen_together_session_ends,
                pretty
            ),
            style = AppTheme.typography.bodySmall,
            fontWeight = if (expiring) FontWeight.SemiBold else FontWeight.Normal,
            color = tone,
            modifier = Modifier.weight(1f)
        )
        if (isHost && extensionsLeft > 0) {
            Text(
                text = stringResource(R.string.listen_together_extend),
                style = AppTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .bounceClick(onClick = onExtend)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** Shows the room's playback control mode; the server validates each action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlModeCard(
    controlMode: String,
    onChange: (String) -> Unit
) {
    val everyone = controlMode == com.ozyern.exhale.listentogether.ControlModes.EVERYONE
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.listen_together_control_mode))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (everyone) R.string.listen_together_control_everyone_desc
                else R.string.listen_together_control_owner_desc
            ),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        run {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !everyone,
                    onClick = { onChange(com.ozyern.exhale.listentogether.ControlModes.OWNER) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.listen_together_control_owner)) }
                SegmentedButton(
                    selected = everyone,
                    onClick = { onChange(com.ozyern.exhale.listentogether.ControlModes.EVERYONE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.listen_together_control_everyone)) }
            }
        }
    }
}
