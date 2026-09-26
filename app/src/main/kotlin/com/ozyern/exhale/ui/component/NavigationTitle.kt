/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ozyern.exhale.R

@Composable
fun NavigationTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    subtitle: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            // Generous Apple-style breathing room: lots of air ABOVE each section header,
            // a little below before its row content starts.
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    ) {
        // Apple's shelf headers carry no artwork: the title and its chevron are the whole header.

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            label?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }

            // Title and chevron as one object, hard against each other.
            //
            // The chevron used to sit at the far trailing edge of the row, which is the Material
            // list idiom: label on the left, affordance on the right, the gap between them
            // meaning "this whole row is tappable". Apple Music does the opposite and it is the
            // single most recognisable thing about its section headers — the disclosure mark
            // follows the last letter of the title, so "Made For You ›" reads as one phrase you
            // press rather than as a heading with a button parked across the screen from it.
            //
            // The title takes `weight(1f, fill = false)`: it shrinks and ellipsises when long, but
            // a short one does not stretch, so the chevron stays glued to the text.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    // Apple-Music header: LARGE, heavy, high-contrast ink with generous air above
                    // each section — the whitespace does the separating, not dividers.
                    // Apple Music's shelf title: Title 2, 22pt bold.
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (onClick != null) {
                    // A small grey chevron, not a full-size forward arrow. The disclosure mark is
                    // deliberately quieter than the title it follows — a 24dp filled arrow read
                    // as an action button sitting inside a heading.
                    Icon(
                        painter = painterResource(R.drawable.chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(20.dp),
                    )
                }

                // A ribbon tied to the end of every section header, when the Sabrina theme is on.
                //
                // After the chevron rather than before the title: the title and its disclosure
                // mark are one phrase you press, and a bow wedged inside that phrase would break
                // it apart. Trailing the whole thing, it decorates the header instead of joining
                // it. Static, unlike the drifting backdrop — nothing should move next to a line
                // someone is reading.
                if (sabrinaDecorEnabled()) {
                    SabrinaBow(
                        color = MaterialTheme.colorScheme.primary,
                        size = 17.dp,
                        rotation = -10f,
                        alpha = 0.9f,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
}
