package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LiveStream
import com.example.data.StreamComment
import com.example.data.UiState
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.SuperChatGold

@Composable
fun LiveFeedScreen(
    streamsState: UiState<List<LiveStream>>,
    selectedStreamId: String?,
    commentsState: UiState<List<StreamComment>>,
    isWideScreen: Boolean,
    onSelectStream: (String?) -> Unit,
    onOpenStudio: () -> Unit,
    onSendComment: (LiveStream, String) -> Unit,
    onSendDonation: (LiveStream, Long, String) -> Unit,
    onStartBackgroundPlayback: (LiveStream) -> Unit
) {
    val strings = LocalAppStrings.current
    val streams = (streamsState as? UiState.Success)?.data.orEmpty()
    val activeStream = streams.firstOrNull { it.id == selectedStreamId }

    if (activeStream != null) {
        BackHandler { onSelectStream(null) }
        WatchStreamRoom(
            stream = activeStream,
            commentsState = commentsState,
            isWideScreen = isWideScreen,
            onBack = { onSelectStream(null) },
            onSendComment = { msg -> onSendComment(activeStream, msg) },
            onSendDonation = { amount, note -> onSendDonation(activeStream, amount, note) },
            onStartBackgroundPlayback = { onStartBackgroundPlayback(activeStream) }
        )
        return
    }

    var filterOnlyLive by remember { mutableStateOf(false) }
    val filteredStreams = remember(streams, filterOnlyLive) {
        if (filterOnlyLive) streams.filter { it.status == "live" } else streams
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.activeBroadcasts,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Real-time Firestore rooms • WebSocket low-latency",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilterChip(
                selected = filterOnlyLive,
                onClick = { filterOnlyLive = !filterOnlyLive },
                label = { Text(strings.liveNow) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (filterOnlyLive) LiveCrimson else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                },
                modifier = Modifier.testTag("filter_live_only_chip")
            )
        }

        when (streamsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is UiState.Error -> {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = streamsState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is UiState.Success -> {
                if (filteredStreams.isEmpty()) {
                    EmptyLiveFeedCard(
                        onOpenStudio = onOpenStudio
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(filteredStreams, key = { it.id }) { stream ->
                            StreamFeedCard(
                                stream = stream,
                                onWatchClick = { onSelectStream(stream.id) },
                                onBackgroundPlayClick = { onStartBackgroundPlayback(stream) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLiveFeedCard(onOpenStudio: () -> Unit) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero),
                    contentDescription = "Start broadcasting illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.noLiveStreamsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.noLiveStreamsSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenStudio,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("empty_open_studio_button")
                ) {
                    Icon(Icons.Filled.Videocam, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.startYourFirstStream)
                }
            }
        }
    }
}

@Composable
private fun StreamFeedCard(
    stream: LiveStream,
    onWatchClick: () -> Unit,
    onBackgroundPlayClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isLive = stream.status == "live"

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .clickable(onClick = onWatchClick)
            .testTag("stream_card_${stream.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                Image(
                    painter = painterResource(
                        id = if (stream.sourceType == "gallery") R.drawable.img_gallery_standby else R.drawable.img_studio_hero
                    ),
                    contentDescription = stream.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.72f))
                            )
                        )
                )

                // Top status badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = if (isLive) LiveCrimson else Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isLive) "● ${strings.liveNow}" else "REPLAY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = stream.resolution.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        if (stream.lowLatencyWs) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        contentDescription = null,
                                        tint = StudioEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${stream.latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioEmerald
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${stream.viewerCount} ${strings.viewers}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Bottom title overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = stream.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stream.hostName} • ${stream.category} • ₹${stream.totalDonationsInr} SuperChat",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBackgroundPlayClick,
                    modifier = Modifier.testTag("bg_play_btn_${stream.id}")
                ) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(strings.backgroundPlayback)
                }
                Button(
                    onClick = onWatchClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.watchStream)
                }
            }
        }
    }
}

@Composable
private fun WatchStreamRoom(
    stream: LiveStream,
    commentsState: UiState<List<StreamComment>>,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onSendComment: (String) -> Unit,
    onSendDonation: (Long, String) -> Unit,
    onStartBackgroundPlayback: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showDonateDialog by remember { mutableStateOf(false) }

    if (showDonateDialog) {
        SuperChatDonationDialog(
            onDismiss = { showDonateDialog = false },
            onConfirm = { amount, note ->
                onSendDonation(amount, note)
                showDonateDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Room Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("watch_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${stream.hostName} • ${stream.resolution} • ₹${stream.totalDonationsInr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onStartBackgroundPlayback,
                modifier = Modifier.testTag("watch_bg_playback_button")
            ) {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = strings.backgroundPlayback,
                    tint = SignalCyan
                )
            }
        }

        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1.25f)) {
                    WatchVideoStage(stream = stream, onStartBackgroundPlayback = onStartBackgroundPlayback)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    LiveChatAndDonationPane(
                        commentsState = commentsState,
                        onSendComment = onSendComment,
                        onOpenDonate = { showDonateDialog = true }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                WatchVideoStage(stream = stream, onStartBackgroundPlayback = onStartBackgroundPlayback)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    LiveChatAndDonationPane(
                        commentsState = commentsState,
                        onSendComment = onSendComment,
                        onOpenDonate = { showDonateDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchVideoStage(
    stream: LiveStream,
    onStartBackgroundPlayback: () -> Unit
) {
    val strings = LocalAppStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        Image(
            painter = painterResource(
                id = if (stream.sourceType == "gallery") R.drawable.img_gallery_standby else R.drawable.img_studio_hero
            ),
            contentDescription = "Live stream stage",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = if (stream.status == "live") LiveCrimson else Color.DarkGray,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (stream.status == "live") "● ${strings.liveNow}" else "ENDED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = "${stream.resolution} • ${stream.bitrateKbps} kbps • WS ${stream.latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.68f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clickable(onClick = onStartBackgroundPlayback)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(strings.backgroundPlayback, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
fun LiveChatAndDonationPane(
    commentsState: UiState<List<StreamComment>>,
    onSendComment: (String) -> Unit,
    onOpenDonate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var draftMessage by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.liveChatAndDonations,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = SuperChatGold.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .border(1.dp, SuperChatGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenDonate)
                    .testTag("open_donate_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.VolunteerActivism,
                        contentDescription = strings.donate,
                        tint = SuperChatGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.donate,
                        style = MaterialTheme.typography.labelLarge,
                        color = SuperChatGold
                    )
                }
            }
        }

        val comments = (commentsState as? UiState.Success)?.data.orEmpty()
        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Say hello in live chat or send a SuperChat donation!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    val isDonation = comment.messageType == "donation"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDonation) {
                            SuperChatGold.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDonation) Modifier.border(
                                    1.dp,
                                    SuperChatGold.copy(alpha = 0.7f),
                                    RoundedCornerShape(14.dp)
                                ) else Modifier
                            )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = if (isDonation) SuperChatGold else SignalCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = comment.authorName,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isDonation) SuperChatGold else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (isDonation) {
                                    Surface(
                                        color = SuperChatGold,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "₹${comment.donationAmount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = comment.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Comment Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draftMessage,
                onValueChange = { draftMessage = it.take(300) },
                placeholder = { Text(strings.sendCommentHint) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("live_comment_input")
            )
            FilledIconButton(
                onClick = {
                    if (draftMessage.isNotBlank()) {
                        onSendComment(draftMessage)
                        draftMessage = ""
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_comment_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send comment",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun SuperChatDonationDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    val strings = LocalAppStrings.current
    val presets = listOf(50L, 100L, 250L, 500L, 1000L)
    var selectedAmount by remember { mutableLongStateOf(100L) }
    var customAmountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Amazing live stream! Keep it up!") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.VolunteerActivism, contentDescription = null, tint = SuperChatGold)
                Text(strings.superChatTitle)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { amount ->
                        FilterChip(
                            selected = customAmountText.isBlank() && selectedAmount == amount,
                            onClick = {
                                selectedAmount = amount
                                customAmountText = ""
                            },
                            label = { Text("₹$amount") }
                        )
                    }
                }
                OutlinedTextField(
                    value = customAmountText,
                    onValueChange = { input ->
                        customAmountText = input.filter { it.isDigit() }.take(6)
                    },
                    label = { Text(strings.customAmountHint) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("donation_custom_amount_input")
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("SuperChat Message") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("donation_message_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAmount = customAmountText.toLongOrNull() ?: selectedAmount
                    onConfirm(finalAmount, note)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuperChatGold,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("confirm_donation_button")
            ) {
                Text(strings.sendDonation, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
