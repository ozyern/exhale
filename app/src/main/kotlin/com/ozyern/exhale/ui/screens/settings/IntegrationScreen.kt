/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.PreferenceGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceGroupTitle
import com.ozyern.exhale.constants.ListenBrainzEnabledKey
import com.ozyern.exhale.constants.ListenBrainzTokenKey
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.InfoLabel
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.component.TextFieldDialog
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (listenBrainzEnabled, onListenBrainzEnabledChange) = rememberPreference(ListenBrainzEnabledKey, false)
    val (listenBrainzToken, onListenBrainzTokenChange) = rememberPreference(ListenBrainzTokenKey, "")

    var showListenBrainzTokenEditor = remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        PreferenceGroupTitle(
                title = stringResource(R.string.general),
            )
        PreferenceGroup {
            PreferenceEntry(
                title = { Text(stringResource(R.string.discord_integration)) },
                icon = { Icon(painterResource(R.drawable.discord), null) },
                onClick = {
                    navController.navigate("settings/discord")
                },
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.scrobbling),
        )
        PreferenceGroup {
            PreferenceEntry(
                title = { Text(stringResource(R.string.lastfm_integration)) },
                icon = { Icon(painterResource(R.drawable.token), null) },
                onClick = {
                    navController.navigate("settings/lastfm")
                },
            )
            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.listenbrainz_scrobbling)) },
                description = stringResource(R.string.listenbrainz_scrobbling_description),
                icon = { Icon(painterResource(R.drawable.token), null) },
                checked = listenBrainzEnabled,
                onCheckedChange = onListenBrainzEnabledChange,
            )
            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text(if (listenBrainzToken.isBlank()) stringResource(R.string.set_listenbrainz_token) else stringResource(R.string.edit_listenbrainz_token)) },
                icon = { Icon(painterResource(R.drawable.token), null) },
                onClick = { showListenBrainzTokenEditor.value = true },
            )
        }

    }

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.integration)) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        }
    )

    if (showListenBrainzTokenEditor.value) {
        TextFieldDialog(
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(listenBrainzToken),
            onDone = { data ->
                onListenBrainzTokenChange(data)
                showListenBrainzTokenEditor.value = false
            },
            onDismiss = { showListenBrainzTokenEditor.value = false },
            singleLine = true,
            maxLines = 1,
            isInputValid = {
                it.isNotEmpty()
            },
            extraContent = {
                InfoLabel(text = stringResource(R.string.listenbrainz_scrobbling_description))
            }
        )
    }
}
