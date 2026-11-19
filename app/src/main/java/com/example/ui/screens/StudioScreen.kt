package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.LiveStream
import com.example.data.StreamComment
import com.example.data.StreamResolution
import com.example.data.StreamSourceType
import com.example.data.UiState
import com.example.i18n.LocalAppStrings
import com.example.network.StreamTransportTelemetry
import com.example.network.WsConnectionState
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.SuperChatGold
import com.example.viewmodel.StudioSessionState

@Composable
fun StudioScreen(
    studioState: StudioSessionState,
    wsTelemetry: StreamTransportTelemetry,
    commentsState: UiState<List<StreamComment>>,
    activeLiveStream: LiveStream?,
    isWideScreen: Boolean,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onResolutionSelect: (StreamResolution) -> Unit,
    onSourceSelect: (StreamSourceType) -> Unit,
    onGalleryVideoSelected: (String?) -> Unit,
    onToggleMic: () -> Unit,
    onFlipCamera: () -> Unit,
    onSaveStreamConfig: (String, String, String, Boolean) -> Unit,
    onTestRtmp: () -> Unit,
    onStartBroadcast: () -> Unit,
    onEndBroadcast: () -> Unit,
    onSendComment: (LiveStream, String) -> Unit,
    onSendDonation: (LiveStream, Long, String) -> Unit,
    onToggleBackgroundPlayback: (String, String) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var hasCameraMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraMicPermission = (result[Manifest.permission.CAMERA] == true) &&
            (result[Manifest.permission.RECORD_AUDIO] == true)
    }

    // Zero-permission Google Play Policy compliant Android Photo Picker for phone gallery videos
    val galleryVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onGalleryVideoSelected(uri.toString())
        }
    }

    var showDonateDialog by remember { mutableStateOf(false) }
    if (showDonateDialog && activeLiveStream != null) {
        SuperChatDonationDialog(
            onDismiss = { showDonateDialog = false },
            onConfirm = { amount, note ->
                onSendDonation(activeLiveStream, amount, note)
                showDonateDialog = false
            }
        )
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StudioMonitorCard(
                    studioState = studioState,
                    wsTelemetry = wsTelemetry,
                    hasCameraMicPermission = hasCameraMicPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                        )
                    },
                    onPickGalleryVideo = {
                        galleryVideoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onToggleMic = onToggleMic,
                    onFlipCamera = onFlipCamera,
                    onStartBroadcast = onStartBroadcast,
                    onEndBroadcast = onEndBroadcast,
                    onBackgroundPlayback = {
                        onToggleBackgroundPlayback(studioState.streamTitle, studioState.resolution.code)
                    }
                )
                StudioControlsSection(
                    studioState = studioState,
                    wsTelemetry = wsTelemetry,
                    onTitleChange = onTitleChange,
                    onCategoryChange = onCategoryChange,
                    onResolutionSelect = onResolutionSelect,
                    onSourceSelect = onSourceSelect,
                    onPickGalleryVideo = {
                        galleryVideoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onSaveStreamConfig = onSaveStreamConfig,
                    onTestRtmp = onTestRtmp
                )
                Spacer(Modifier.height(24.dp))
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            ) {
                if (activeLiveStream != null) {
                    LiveChatAndDonationPane(
                        commentsState = commentsState,
                        onSendComment = { msg -> onSendComment(activeLiveStream, msg) },
                        onOpenDonate = { showDonateDialog = true }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.RadioButtonChecked,
                                contentDescription = null,
                                tint = LiveCrimson,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = strings.liveChatAndDonations,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Tap Go Live to open real-time viewer comments & SuperChat donations right beside your monitor.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StudioMonitorCard(
                studioState = studioState,
                wsTelemetry = wsTelemetry,
                hasCameraMicPermission = hasCameraMicPermission,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                    )
                },
                onPickGalleryVideo = {
                    galleryVideoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onToggleMic = onToggleMic,
                onFlipCamera = onFlipCamera,
                onStartBroadcast = onStartBroadcast,
                onEndBroadcast = onEndBroadcast,
                onBackgroundPlayback = {
                    onToggleBackgroundPlayback(studioState.streamTitle, studioState.resolution.code)
                }
            )

            if (activeLiveStream != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    LiveChatAndDonationPane(
                        commentsState = commentsState,
                        onSendComment = { msg -> onSendComment(activeLiveStream, msg) },
                        onOpenDonate = { showDonateDialog = true }
                    )
                }
            }

            StudioControlsSection(
                studioState = studioState,
                wsTelemetry = wsTelemetry,
                onTitleChange = onTitleChange,
                onCategoryChange = onCategoryChange,
                onResolutionSelect = onResolutionSelect,
                onSourceSelect = onSourceSelect,
                onPickGalleryVideo = {
                    galleryVideoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onSaveStreamConfig = onSaveStreamConfig,
                onTestRtmp = onTestRtmp
            )

            Spacer(Modifier.height(84.dp))
        }
    }
}

@Composable
private fun StudioMonitorCard(
    studioState: StudioSessionState,
    wsTelemetry: StreamTransportTelemetry,
    hasCameraMicPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPickGalleryVideo: () -> Unit,
    onToggleMic: () -> Unit,
    onFlipCamera: () -> Unit,
    onStartBroadcast: () -> Unit,
    onEndBroadcast: () -> Unit,
    onBackgroundPlayback: () -> Unit
) {
    val strings = LocalAppStrings.current
    val timerText = remember(studioState.elapsedSeconds) {
        val h = studioState.elapsedSeconds / 3600
        val m = (studioState.elapsedSeconds % 3600) / 60
        val s = studioState.elapsedSeconds % 60
        "%02d:%02d:%02d".format(h, m, s)
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (studioState.liveActive) 1.5.dp else 1.dp,
                color = if (studioState.liveActive) LiveCrimson else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(Color.Black)
            ) {
                when (studioState.sourceType) {
                    StreamSourceType.CAMERA -> {
                        if (hasCameraMicPermission) {
                            CameraXLivePreview(
                                useFrontCamera = studioState.frontCamera,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_studio_hero),
                                contentDescription = "Camera standby",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = onRequestPermission,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("grant_camera_mic_button")
                                ) {
                                    Icon(Icons.Filled.Videocam, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.grantCameraMic)
                                }
                            }
                        }
                    }

                    StreamSourceType.GALLERY -> {
                        val videoUri = studioState.galleryVideoUri
                        if (!videoUri.isNullOrBlank()) {
                            GalleryVideoPreview(
                                videoUriString = videoUri,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.img_gallery_standby),
                                contentDescription = "Gallery standby",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = onPickGalleryVideo,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.testTag("monitor_pick_gallery_button")
                                ) {
                                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(strings.pickGalleryVideo)
                                }
                            }
                        }
                    }
                }

                // Top Telemetry HUD
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = if (studioState.liveActive) LiveCrimson else Color.Black.copy(alpha = 0.68f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (studioState.liveActive) "● ${strings.liveNow} $timerText" else "STANDBY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = Color.Black.copy(alpha = 0.68f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${studioState.resolution.code} • ${studioState.resolution.targetBitrateKbps / 1000.0}Mbps",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    val wsConnected = wsTelemetry.wsState == WsConnectionState.CONNECTED
                    Surface(
                        color = Color.Black.copy(alpha = 0.68f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = if (wsConnected) StudioEmerald else SuperChatGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (wsConnected) "${strings.wsConnected} ${wsTelemetry.latencyMs}ms" else strings.wsStandby,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (wsConnected) StudioEmerald else SuperChatGold
                            )
                        }
                    }
                }
            }

            // Broadcast Action Deck
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onToggleMic,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (studioState.micMuted) LiveCrimson.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("toggle_mic_button")
                    ) {
                        Icon(
                            imageVector = if (studioState.micMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = if (studioState.micMuted) "Unmute microphone" else "Mute microphone",
                            tint = if (studioState.micMuted) LiveCrimson else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (studioState.sourceType == StreamSourceType.CAMERA) {
                        IconButton(
                            onClick = onFlipCamera,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .testTag("flip_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch camera",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = onBackgroundPlayback,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("studio_bg_playback_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = strings.backgroundPlayback,
                            tint = SignalCyan
                        )
                    }
                }

                Button(
                    onClick = { if (studioState.liveActive) onEndBroadcast() else onStartBroadcast() },
                    enabled = !studioState.busy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (studioState.liveActive) Color(0xFF2C3248) else LiveCrimson,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("go_live_button")
                ) {
                    if (studioState.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (studioState.liveActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (studioState.liveActive) strings.endStream else strings.goLive,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraXLivePreview(
    useFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(useFrontCamera) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = if (useFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun GalleryVideoPreview(
    videoUriString: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    start()
                }
                setVideoURI(Uri.parse(videoUriString))
            }
        },
        update = { view ->
            val targetUri = Uri.parse(videoUriString)
            if (view.tag != videoUriString) {
                view.tag = videoUriString
                view.setVideoURI(targetUri)
                view.start()
            }
        },
        modifier = modifier
    )
}

@Composable
private fun StudioControlsSection(
    studioState: StudioSessionState,
    wsTelemetry: StreamTransportTelemetry,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onResolutionSelect: (StreamResolution) -> Unit,
    onSourceSelect: (StreamSourceType) -> Unit,
    onPickGalleryVideo: () -> Unit,
    onSaveStreamConfig: (String, String, String, Boolean) -> Unit,
    onTestRtmp: () -> Unit
) {
    val strings = LocalAppStrings.current
    var rtmpUrlDraft by remember(studioState.rtmpUrl) { mutableStateOf(studioState.rtmpUrl) }
    var streamKeyDraft by remember(studioState.streamKey) { mutableStateOf(studioState.streamKey) }
    var wsRelayDraft by remember(studioState.wsRelayUrl) { mutableStateOf(studioState.wsRelayUrl) }
    var lowLatencyDraft by remember(studioState.lowLatencyEnabled) { mutableStateOf(studioState.lowLatencyEnabled) }
    var revealKey by remember { mutableStateOf(false) }

    // 1. Source & Resolution Quality Selector
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Video Source & ${strings.qualityLabel}",
                style = MaterialTheme.typography.titleMedium
            )

            // Camera vs Gallery Video Source
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = studioState.sourceType == StreamSourceType.CAMERA,
                    onClick = { onSourceSelect(StreamSourceType.CAMERA) },
                    label = { Text(strings.cameraSource) },
                    leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("source_camera_chip")
                )
                FilterChip(
                    selected = studioState.sourceType == StreamSourceType.GALLERY,
                    onClick = { onSourceSelect(StreamSourceType.GALLERY) },
                    label = { Text(strings.gallerySource) },
                    leadingIcon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("source_gallery_chip")
                )
            }

            if (studioState.sourceType == StreamSourceType.GALLERY) {
                OutlinedButton(
                    onClick = onPickGalleryVideo,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pick_gallery_video_button")
                ) {
                    Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (studioState.galleryVideoUri != null) "${strings.galleryReady} (Change)"
                        else strings.pickGalleryVideo
                    )
                }
            }

            // 480p / 720p / 1080p Resolution Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StreamResolution.entries.forEach { res ->
                    val selected = studioState.resolution == res
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) SignalCyan.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) SignalCyan else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onResolutionSelect(res) }
                            .testTag("quality_${res.code}_pill")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = res.code,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) SignalCyan else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${res.targetBitrateKbps / 1000.0}Mbps • ${res.fps}fps",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Stream Metadata
            OutlinedTextField(
                value = studioState.streamTitle,
                onValueChange = onTitleChange,
                label = { Text(strings.streamTitleLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("studio_title_input")
            )
            OutlinedTextField(
                value = studioState.streamCategory,
                onValueChange = onCategoryChange,
                label = { Text(strings.streamCategoryLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("studio_category_input")
            )
        }
    }

    // 2. YouTube RTMP URL, Secret Stream Key & Low-Latency WebSocket Setup
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.SettingsInputAntenna, contentDescription = null, tint = LiveCrimson)
                Text(
                    text = strings.rtmpConfigTitle,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = rtmpUrlDraft.contains("a.rtmps.youtube.com"),
                    onClick = { rtmpUrlDraft = "rtmps://a.rtmps.youtube.com/live2" },
                    label = { Text("YouTube RTMPS") }
                )
                FilterChip(
                    selected = rtmpUrlDraft.contains("b.rtmp.youtube.com"),
                    onClick = { rtmpUrlDraft = "rtmp://b.rtmp.youtube.com/live2?backup=1" },
                    label = { Text("YT Backup") }
                )
            }

            OutlinedTextField(
                value = rtmpUrlDraft,
                onValueChange = { rtmpUrlDraft = it.take(300) },
                label = { Text(strings.rtmpUrlLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rtmp_url_input")
            )

            OutlinedTextField(
                value = streamKeyDraft,
                onValueChange = { streamKeyDraft = it.take(200) },
                label = { Text(strings.streamKeyLabel) },
                placeholder = { Text("xxxx-xxxx-xxxx-xxxx") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle stream key visibility"
                        )
                    }
                },
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("stream_key_input")
            )

            OutlinedTextField(
                value = wsRelayDraft,
                onValueChange = { wsRelayDraft = it.take(300) },
                label = { Text(strings.wsRelayLabel) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ws_relay_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.lowLatencyMode, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "OkHttp WebSocket telemetry & sub-second chat relay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = lowLatencyDraft,
                    onCheckedChange = { lowLatencyDraft = it },
                    modifier = Modifier.testTag("low_latency_ws_switch")
                )
            }

            if (wsTelemetry.rtmpStatusNote.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (wsTelemetry.rtmpReachable == true) {
                        StudioEmerald.copy(alpha = 0.14f)
                    } else {
                        SuperChatGold.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = wsTelemetry.rtmpStatusNote,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (wsTelemetry.rtmpReachable == true) StudioEmerald else SuperChatGold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTestRtmp,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_rtmp_button")
                ) {
                    Text("Test Ingest")
                }
                Button(
                    onClick = {
                        onSaveStreamConfig(rtmpUrlDraft, streamKeyDraft, wsRelayDraft, lowLatencyDraft)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("save_stream_config_button")
                ) {
                    Text(strings.saveStreamConfig)
                }
            }
        }
    }
}
