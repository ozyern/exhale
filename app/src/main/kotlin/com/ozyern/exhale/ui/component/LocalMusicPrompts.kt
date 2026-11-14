/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.iosRowPress
import com.ozyern.exhale.ui.screens.library.apple.SfStyle

/** The runtime permission that gates reading audio files on this Android version. */
fun localAudioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun hasLocalAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, localAudioPermission()) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/**
 * Asks for access to on-device audio and tells the caller how it went.
 *
 * Once Android has stopped showing the system dialog (two denials, or "don't ask again"),
 * launching the request again does nothing, so a button that keeps asking looks broken. This
 * reports that state so the prompt can send the user to app settings instead.
 */
@Composable
fun rememberLocalAudioPermission(onResult: (granted: Boolean) -> Unit): LocalAudioPermission {
    val context = LocalContext.current
    val permission = remember { localAudioPermission() }
    val preferences = remember(context) { context.getSharedPreferences("local_audio_access", Context.MODE_PRIVATE) }
    val activity = remember(context) {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        current as? Activity
    }
    var blocked by remember { mutableStateOf(preferences.getBoolean(permission, false) && !hasLocalAudioPermission(context)) }
    var granted by remember { mutableStateOf(hasLocalAudioPermission(context)) }
    val currentOnResult by rememberUpdatedState(onResult)
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        val current = hasLocalAudioPermission(context)
        blocked = !current && preferences.getBoolean(permission, false)
        if (current) preferences.edit().remove(permission).apply()
        if (granted != current) {
            granted = current
            if (current) blocked = false
            currentOnResult(current)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        blocked = !allowed && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        preferences.edit().putBoolean(permission, blocked).apply()
        currentOnResult(allowed)
    }
    return remember(launcher, blocked, granted) {
        LocalAudioPermission(
            blocked = blocked,
            request = { if (blocked) openAppSettings(context) else launcher.launch(permission) },
            granted = granted,
        )
    }
}

class LocalAudioPermission(val blocked: Boolean, val request: () -> Unit, val granted: Boolean = false)

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Full-width prompt explaining the storage request and offering Settings when the
 * system permission dialog can no longer be shown.
 */
@Composable
fun LocalMusicPermissionPrompt(
    permission: LocalAudioPermission,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    PromptLayout(
        icon = R.drawable.library_music,
        title = stringResource(R.string.local_permission_title),
        body = stringResource(
            if (permission.blocked) R.string.local_permission_blocked_body else R.string.local_permission_body,
        ),
        modifier = modifier,
    ) {
        PromptAction(
            label = stringResource(if (permission.blocked) R.string.local_permission_open_settings else R.string.local_permission_allow),
            onClick = { if (permission.blocked) openAppSettings(context) else permission.request() },
        )
    }
}

/** Shown when access is granted but the device scan found no audio. */
@Composable
fun LocalMusicEmptyState(
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PromptLayout(
        icon = R.drawable.music_note,
        title = stringResource(R.string.local_empty_title),
        body = stringResource(R.string.local_empty_body),
        modifier = modifier,
    ) {
        PromptAction(stringResource(R.string.local_empty_scan), onScan)
    }
}

@Composable
private fun PromptLayout(
    icon: Int,
    title: String,
    body: String,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(IosTheme.tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = IosTheme.tint,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = SfStyle(22.sp, 28.sp, FontWeight.Bold, (-0.26).sp),
            color = IosTheme.colors.label,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = SfStyle(15.sp, 20.sp, tracking = (-0.23).sp),
            color = IosTheme.colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        action()
    }
}

@Composable
private fun PromptAction(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(CircleShape)
            .background(IosTheme.tint).iosRowPress(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = SfStyle(17.sp, 22.sp, FontWeight.SemiBold, (-0.43).sp), color = Color.White)
    }
}
