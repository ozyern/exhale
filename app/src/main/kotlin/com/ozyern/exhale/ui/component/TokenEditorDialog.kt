/*
 * Exhale Project (2026)
 * Licensed under GPL-3.0
 */

package com.ozyern.exhale.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AccountChannelHandleKey
import com.ozyern.exhale.constants.AccountEmailKey
import com.ozyern.exhale.constants.AccountNameKey
import com.ozyern.exhale.constants.DataSyncIdKey
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.PoTokenKey
import com.ozyern.exhale.constants.VisitorDataKey
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.utils.parseCookieString
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.reportException
import kotlinx.coroutines.launch

@Composable
fun TokenEditorDialog(
    onDismiss: () -> Unit,
    onSuccess: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var accountNamePref by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var poToken by rememberPreference(PoTokenKey, "")
    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")

    val initialText = remember(
        innerTubeCookie,
        visitorData,
        dataSyncId,
        poToken,
        accountNamePref,
        accountEmail,
        accountChannelHandle
    ) {
        """
            ***INNERTUBE COOKIE*** =$innerTubeCookie
            ***VISITOR DATA*** =$visitorData
            ***DATASYNC ID*** =$dataSyncId
            ***PO TOKEN*** =${YouTube.poToken.orEmpty().ifBlank { poToken }}
            ***ACCOUNT NAME*** =$accountNamePref
            ***ACCOUNT EMAIL*** =$accountEmail
            ***ACCOUNT CHANNEL HANDLE*** =$accountChannelHandle
        """.trimIndent()
    }

    var textFieldValue by remember(initialText) { mutableStateOf(TextFieldValue(initialText)) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    TextFieldDialog(
        title = { Text(stringResource(R.string.advanced_login)) },
        initialTextFieldValue = textFieldValue,
        onDone = { data ->
            var cookie = ""
            var visitorDataValue = ""
            var dataSyncIdValue = ""
            var poTokenValue = ""
            var authUserValue = "0"
            var accountNameValue = ""
            var accountEmailValue = ""
            var accountChannelHandleValue = ""

            if (data.contains("***INNERTUBE COOKIE*** =")) {
                data.lines().forEach { line ->
                    when {
                        line.startsWith("***INNERTUBE COOKIE*** =") -> cookie = line.substringAfter("=").trim()
                        line.startsWith("***VISITOR DATA*** =") -> visitorDataValue = line.substringAfter("=").trim()
                        line.startsWith("***DATASYNC ID*** =") -> dataSyncIdValue = line.substringAfter("=").trim()
                        line.startsWith("***PO TOKEN*** =") -> poTokenValue = line.substringAfter("=").trim()
                        line.startsWith("***AUTH USER*** =") -> authUserValue = line.substringAfter("=").trim()
                        line.startsWith("***ACCOUNT NAME*** =") -> accountNameValue = line.substringAfter("=").trim()
                        line.startsWith("***ACCOUNT EMAIL*** =") -> accountEmailValue = line.substringAfter("=").trim()
                        line.startsWith("***ACCOUNT CHANNEL HANDLE*** =") -> accountChannelHandleValue = line.substringAfter("=").trim()
                    }
                }
            } else if ("SAPISID" in parseCookieString(data)) {
                cookie = data.trim()
            }

            if (cookie.isNotBlank()) {
                innerTubeCookie = cookie
                YouTube.cookie = cookie
                if (visitorDataValue.isNotBlank()) {
                    visitorData = visitorDataValue
                    YouTube.visitorData = visitorDataValue
                }
                if (dataSyncIdValue.isNotBlank()) {
                    dataSyncId = dataSyncIdValue
                    YouTube.dataSyncId = dataSyncIdValue
                }
                if (poTokenValue.isNotBlank()) {
                    poToken = poTokenValue
                    YouTube.poToken = poTokenValue
                }
                if (accountNameValue.isNotBlank()) accountNamePref = accountNameValue
                if (accountEmailValue.isNotBlank()) accountEmail = accountEmailValue
                if (accountChannelHandleValue.isNotBlank()) accountChannelHandle = accountChannelHandleValue

                coroutineScope.launch {
                    YouTube.accountInfo().onSuccess {
                        accountNamePref = it.name
                        accountEmail = it.email.orEmpty()
                        accountChannelHandle = it.channelHandle.orEmpty()
                    }.onFailure {
                        reportException(it)
                    }
                }

                Toast.makeText(context, R.string.token_login_success, Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            }
        },
        onDismiss = onDismiss,
        singleLine = false,
        maxLines = 20,
        isInputValid = { fullText ->
            val cookieLine = fullText.lines()
                .find { it.startsWith("***INNERTUBE COOKIE*** =") }
            val cookieValue = cookieLine?.substringAfter("***INNERTUBE COOKIE*** =")?.trim()
                ?: if ("SAPISID" in parseCookieString(fullText)) fullText.trim() else ""
            cookieValue.isNotEmpty() && "SAPISID" in parseCookieString(cookieValue)
        },
        extraContent = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = {
                            val textToCopy = textFieldValue.text
                            if (textToCopy.isNotBlank()) {
                                val clip = ClipData.newPlainText("Exhale Token", textToCopy)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, R.string.token_copied, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(stringResource(R.string.copy_token))
                    }

                    FilledTonalButton(
                        onClick = {
                            val clip = clipboardManager.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val pastedText = clip.getItemAt(0).text?.toString().orEmpty()
                                if (pastedText.isNotBlank()) {
                                    textFieldValue = TextFieldValue(pastedText)
                                    Toast.makeText(context, R.string.token_pasted, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.input),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(stringResource(R.string.paste_token))
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        textFieldValue = TextFieldValue("")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.clear_token))
                }
            }
        }
    )
}
