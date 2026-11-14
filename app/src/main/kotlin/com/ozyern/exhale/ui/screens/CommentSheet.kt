package com.ozyern.exhale.ui.screens

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ozyern.exhale.ui.ios.compat.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.comment.CommentRenderer
import com.music.innertube.models.comment.CommentThreadRenderer
import com.ozyern.exhale.R
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFBubbleLeftAndBubbleRight
import com.composables.sfsymbols.monochrome.SFExclamationmarkTriangle
import com.composables.sfsymbols.monochrome.SFHandThumbsup
import com.composables.sfsymbols.monochrome.SFXmark
import kotlinx.coroutines.launch
import com.ozyern.exhale.ui.component.IosGrabber
import zone.ien.hig.CupertinoModalBottomSheet
import zone.ien.hig.PresentationDetent
import zone.ien.hig.PresentationStyle
import zone.ien.hig.rememberCupertinoSheetState

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.ui.utils.combinedBounceClick
import com.ozyern.exhale.utils.reportException
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CommentSheet(
    videoId: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberCupertinoSheetState(
        presentationStyle = PresentationStyle.Modal(
            detents = setOf(PresentationDetent.Large),
        ),
    )

    var comments by remember { mutableStateOf<List<CommentThreadRenderer>>(emptyList()) }
    var nextToken by remember { mutableStateOf<String?>(null) }
    var totalComments by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    val navigator = rememberListDetailPaneScaffoldNavigator<CommentThreadRenderer>()

    LaunchedEffect(videoId) {
        YouTube.comments(videoId).onSuccess { (initialComments, token) ->
            comments = initialComments
            nextToken = token
            isLoading = false
        }.onFailure {
            reportException(it)
            isLoading = false
            isError = true
        }
    }

    CupertinoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { IosGrabber() },
        containerColor = AppTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.comments),
                        style = AppTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (totalComments != null) {
                        Text(
                            text = "$totalComments",
                            style = AppTheme.typography.labelMedium,
                            color = AppTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) onDismiss()
                    }
                }) {
                    SymbolIcon(symbol = sf("SFXmark"), contentDescription = stringResource(R.string.close))
                }
            }

            HorizontalDivider()

            when {
                isLoading && comments.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
                (isError || comments.isEmpty()) && !isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SymbolIcon(symbol = sf("SFExclamationmarkTriangle"), contentDescription = null, size = 48.dp, tint = AppTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = stringResource(R.string.no_comments))
                        }
                    }
                }
                else -> {
                    ListDetailPaneScaffold(
                        directive = navigator.scaffoldDirective,
                        scaffoldState = navigator.scaffoldState,
                        modifier = Modifier.fillMaxHeight(),
                        listPane = {
                            AnimatedPane {
                                LazyColumn(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    items(
                                        comments,
                                        key = { item -> item.comment?.commentRenderer?.commentId ?: "comment-${item.hashCode()}" }
                                    ) { thread ->
                                        val renderer = thread.comment?.commentRenderer ?: return@items
                                        CommentItem(
                                            renderer = renderer,
                                            onShowReplies = {
                                                scope.launch {
                                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, thread)
                                                }
                                            }
                                        )
                                    }

                                    val continuationToken = nextToken
                                    if (continuationToken != null) {
                                        item(key = "pagination_loader") {
                                            LaunchedEffect(continuationToken) {
                                                YouTube.commentContinuation(continuationToken).onSuccess { (newComments, token) ->
                                                    comments = comments + newComments
                                                    nextToken = token
                                                }
                                            }
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        detailPane = {
                            AnimatedPane {
                                val selectedThread = navigator.currentDestination?.contentKey
                                if (selectedThread != null) {
                                    CommentDetailPane(
                                        thread = selectedThread,
                                        onBack = {
                                            scope.launch {
                                                navigator.navigateBack(BackNavigationBehavior.PopUntilScaffoldValueChange)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    renderer: CommentRenderer,
    onShowReplies: (() -> Unit)? = null,
    isReply: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onShowReplies != null) Modifier.bounceClick { onShowReplies() } else Modifier)
            .padding(vertical = if (isReply) 4.dp else 0.dp)
    ) {
        AsyncImage(
            model = renderer.authorThumbnail?.thumbnails?.lastOrNull()?.url,
            contentDescription = null,
            modifier = Modifier
                .size(if (isReply) 28.dp else 36.dp)
                .clip(AppTheme.shapes.extraLarge),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = renderer.authorText?.runs?.firstOrNull()?.text ?: "",
                    style = AppTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = renderer.publishedTimeText?.runs?.firstOrNull()?.text ?: "",
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = renderer.contentText?.runs?.joinToString("") { it.text } ?: "",
                style = AppTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val voteCount = renderer.voteCount
                if (voteCount != null) {
                    SymbolIcon(symbol = sf("SFHandThumbsup"), contentDescription = null, size = 14.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = voteCount.runs?.firstOrNull()?.text ?: "0",
                        style = AppTheme.typography.labelSmall,
                        color = AppTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                val replyCount = renderer.replyCount ?: 0
                if (replyCount > 0 && onShowReplies != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.bounceClick(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onShowReplies() }
                    ) {
                        SymbolIcon(symbol = sf("SFBubbleLeftAndBubbleRight"), contentDescription = "Replies", size = 14.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = replyCount.toString(),
                            style = AppTheme.typography.labelSmall,
                            color = AppTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentDetailPane(
    thread: CommentThreadRenderer,
    onBack: () -> Unit
) {
    val mainComment = thread.comment?.commentRenderer ?: return
    var replies by remember { mutableStateOf<List<CommentRenderer>>(emptyList()) }
    var repliesNextToken by remember { mutableStateOf<String?>(null) }
    var isLoadingReplies by remember { mutableStateOf(false) }

    LaunchedEffect(thread) {
        val initialToken = thread.replies?.commentRepliesRenderer
            ?.contents?.firstOrNull()?.continuationItemRenderer
            ?.continuationEndpoint?.continuationCommand?.token
            ?: thread.replies?.commentRepliesRenderer?.viewReplies?.buttonRenderer?.command?.continuationCommand?.token
            ?: thread.replies?.commentRepliesRenderer?.viewReplies?.buttonRenderer?.navigationEndpoint?.continuationCommand?.token
        

        if (initialToken != null) {
            isLoadingReplies = true
            YouTube.commentReplies(initialToken).onSuccess { (newReplies, token) ->
                replies = replies + newReplies
                repliesNextToken = token
                isLoadingReplies = false
            }.onFailure {
                reportException(it)
                isLoadingReplies = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "${mainComment.replyCount ?: 0} Replies",
                style = AppTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CommentItem(renderer = mainComment, onShowReplies = null)
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(
                replies,
                key = { item -> item.commentId ?: "reply-${item.hashCode()}" }
            ) { replyRenderer ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(36.dp))
                    CommentItem(renderer = replyRenderer, isReply = true)
                }
            }
            
            val continuationToken = repliesNextToken
            if (continuationToken != null) {
                item(key = "replies_pagination") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingReplies) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }

                    LaunchedEffect(continuationToken) {
                        if (isLoadingReplies) return@LaunchedEffect
                        
                        // Debounce: let the UI settle before starting the network request
                        kotlinx.coroutines.delay(500L)
                        
                        isLoadingReplies = true
                        YouTube.commentReplies(continuationToken).onSuccess { (newReplies, token) ->
                            replies = (replies + newReplies).distinctBy { it.commentId ?: it.hashCode() }
                            repliesNextToken = token
                            isLoadingReplies = false
                        }.onFailure {
                            isLoadingReplies = false
                        }
                    }
                }
            }
        }
    }
}
