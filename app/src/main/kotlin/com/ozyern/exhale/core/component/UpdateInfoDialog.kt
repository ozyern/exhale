/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.core.component

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import com.ozyern.exhale.ui.ios.compat.BasicAlertDialog
import com.ozyern.exhale.ui.ios.compat.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Surface
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFChevronRight
import com.composables.sfsymbols.monochrome.SFInfoCircle
import com.composables.sfsymbols.monochrome.SFMagnifyingglass
import com.ozyern.exhale.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateInfoDialog(
    onDismiss: () -> Unit
) {
    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 30.dp,
        cornerRadiusTR = 30.dp,
        cornerRadiusBL = 30.dp,
        cornerRadiusBR = 30.dp,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentBL = 60,
        smoothnessAsPercentBR = 60,
    )
    val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
    val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = 320.dp),
            shape = cardShape,
            color = AppTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    shape = blockShape,
                    color = AppTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = AbsoluteSmoothCornerShape(12.dp, 60),
                                color = AppTheme.colorScheme.secondaryContainer,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SymbolIcon(symbol = sf("SFInfoCircle"), contentDescription = null, size = 16.dp, tint = AppTheme.colorScheme.onSecondaryContainer)
                                    Text(
                                        text = stringResource(R.string.update_info_badge),
                                        style = AppTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.update_info_title),
                            style = AppTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.clear_downloaded_updates_auto_info),
                            style = AppTheme.typography.bodyMedium,
                            color = AppTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    modifier = Modifier,
                    shape = blockShape,
                    color = AppTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SymbolIcon(symbol = sf("SFMagnifyingglass"), contentDescription = null, size = 18.dp, modifier = Modifier
                                .padding(top = 2.dp)
                                , tint = AppTheme.colorScheme.primary)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.manual_delete_hint_title),
                                style = AppTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.manual_delete_hint_desc),
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        shape = actionShape,
                    ) {
                        SymbolIcon(symbol = sf("SFChevronRight"), contentDescription = null, size = 18.dp)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}
