package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.LocalAppPreferences
import com.example.data.PreferencesRepository
import com.example.data.StreamPulseRepository
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.i18n.LocalAppStrings
import com.example.i18n.ProvideAppStrings
import com.example.service.BackgroundPlaybackState
import com.example.service.StreamNotificationHelper
import com.example.service.StreamPlaybackService
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LiveFeedScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.StreamPulseTheme
import com.example.viewmodel.StreamPulseViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class AppTab(
    val routeId: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LIVE("live_feed", Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
    STUDIO("studio", Icons.Filled.Videocam, Icons.Outlined.Videocam),
    ANALYTICS("analytics", Icons.Filled.Analytics, Icons.Outlined.Analytics),
    PROFILE("profile_settings", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun StreamPulseApp(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val preferencesRepository = remember(context) { PreferencesRepository(context) }
    val localPrefs by preferencesRepository.preferencesFlow
        .collectAsStateWithLifecycle(initialValue = LocalAppPreferences())

    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val useDarkTheme = when (localPrefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    StreamPulseTheme(darkTheme = useDarkTheme) {
        ProvideAppStrings(language = localPrefs.language) {
            val user = currentUser
            if (user == null) {
                AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
            } else {
                key(user.uid) {
                    AuthenticatedStreamPulseShell(
                        currentUserId = user.uid,
                        displayName = user.displayName ?: "Creator",
                        email = user.email ?: "",
                        photoUrl = user.photoUrl?.toString(),
                        preferencesRepository = preferencesRepository
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedStreamPulseShell(
    currentUserId: String,
    displayName: String,
    email: String,
    photoUrl: String?,
    preferencesRepository: PreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StreamPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StreamPulseViewModel(
                    repository = StreamPulseRepository(db),
                    preferencesRepository = preferencesRepository,
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email,
                    initialPhotoUrl = photoUrl
                )
            }
        }
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val preferences by viewModel.localPreferences.collectAsStateWithLifecycle()
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val wsTelemetry by viewModel.wsTelemetry.collectAsStateWithLifecycle()
    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val publicStreamsState by viewModel.publicStreamsState.collectAsStateWithLifecycle()
    val creatorStreamsState by viewModel.creatorStreamsState.collectAsStateWithLifecycle()
    val activationKeysState by viewModel.activationKeysState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val selectedWatchStreamId by viewModel.selectedWatchStreamId.collectAsStateWithLifecycle()
    val activeCommentsState by viewModel.activeCommentsState.collectAsStateWithLifecycle()
    val bgPlaybackState by StreamPlaybackService.playbackState.collectAsStateWithLifecycle()

    var activeTabName by rememberSaveable { mutableStateOf(AppTab.STUDIO.name) }
    val activeTab = remember(activeTabName) {
        AppTab.entries.firstOrNull { it.name == activeTabName } ?: AppTab.STUDIO
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.pushAlertEvents.collect { alert ->
            StreamNotificationHelper.showPushAlert(context, alert.title, alert.body)
        }
    }

    val activeLiveStream = remember(publicStreamsState, studioState.activeStreamId) {
        val list = (publicStreamsState as? UiState.Success)?.data.orEmpty()
        list.firstOrNull { it.id == studioState.activeStreamId }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        val tabLabel: (AppTab) -> String = { tab ->
            when (tab) {
                AppTab.LIVE -> strings.tabLive
                AppTab.STUDIO -> strings.tabStudio
                AppTab.ANALYTICS -> strings.tabAnalytics
                AppTab.PROFILE -> strings.tabProfile
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideScreen) {
                    Column {
                        if (bgPlaybackState.active) {
                            MiniBackgroundPlaybackBar(
                                state = bgPlaybackState,
                                onTogglePause = { StreamPlaybackService.togglePause(context) },
                                onStop = { StreamPlaybackService.stop(context) }
                            )
                        }
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { activeTabName = tab.name },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tabLabel(tab)
                                        )
                                    },
                                    label = { Text(tabLabel(tab)) },
                                    modifier = Modifier.testTag("nav_${tab.routeId}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { activeTabName = tab.name },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tabLabel(tab)
                                    )
                                },
                                label = { Text(tabLabel(tab)) },
                                modifier = Modifier.testTag("rail_${tab.routeId}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (isWideScreen && bgPlaybackState.active) {
                        MiniBackgroundPlaybackBar(
                            state = bgPlaybackState,
                            onTogglePause = { StreamPlaybackService.togglePause(context) },
                            onStop = { StreamPlaybackService.stop(context) }
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activeTab) {
                            AppTab.LIVE -> LiveFeedScreen(
                                streamsState = publicStreamsState,
                                selectedStreamId = selectedWatchStreamId,
                                commentsState = activeCommentsState,
                                isWideScreen = isWideScreen,
                                onSelectStream = viewModel::selectStreamToWatch,
                                onOpenStudio = { activeTabName = AppTab.STUDIO.name },
                                onSendComment = viewModel::sendLiveComment,
                                onSendDonation = viewModel::sendSuperChatDonation,
                                onStartBackgroundPlayback = { stream ->
                                    StreamPlaybackService.start(
                                        context = context,
                                        title = stream.title,
                                        host = stream.hostName,
                                        quality = stream.resolution
                                    )
                                }
                            )

                            AppTab.STUDIO -> StudioScreen(
                                studioState = studioState,
                                wsTelemetry = wsTelemetry,
                                commentsState = activeCommentsState,
                                activeLiveStream = activeLiveStream,
                                isWideScreen = isWideScreen,
                                onTitleChange = viewModel::updateStudioTitle,
                                onCategoryChange = viewModel::updateStudioCategory,
                                onResolutionSelect = viewModel::setResolution,
                                onSourceSelect = viewModel::updateSourceType,
                                onGalleryVideoSelected = viewModel::setGalleryVideoUri,
                                onToggleMic = viewModel::toggleMicMute,
                                onFlipCamera = viewModel::toggleCameraFacing,
                                onSaveStreamConfig = viewModel::saveStreamConfiguration,
                                onTestRtmp = viewModel::testRtmpConnection,
                                onStartBroadcast = viewModel::startBroadcast,
                                onEndBroadcast = viewModel::endBroadcast,
                                onSendComment = viewModel::sendLiveComment,
                                onSendDonation = viewModel::sendSuperChatDonation,
                                onToggleBackgroundPlayback = { title, quality ->
                                    if (bgPlaybackState.active) {
                                        StreamPlaybackService.stop(context)
                                    } else {
                                        StreamPlaybackService.start(
                                            context = context,
                                            title = title,
                                            host = displayName,
                                            quality = quality
                                        )
                                    }
                                }
                            )

                            AppTab.ANALYTICS -> AnalyticsScreen(
                                creatorStreamsState = creatorStreamsState,
                                isWideScreen = isWideScreen
                            )

                            AppTab.PROFILE -> ProfileSettingsScreen(
                                userProfileState = userProfileState,
                                preferences = preferences,
                                activationKeysState = activationKeysState,
                                notificationsState = notificationsState,
                                feedbackState = feedbackState,
                                onSaveProfile = viewModel::saveProfile,
                                onSelectTheme = viewModel::setThemeMode,
                                onSelectLanguage = viewModel::setLanguage,
                                onToggleNotifications = viewModel::setNotificationsEnabled,
                                onSendTestAlert = viewModel::sendTestPushAlert,
                                onMarkNotificationRead = viewModel::markNotificationRead,
                                onToggleBackgroundPlaybackPref = viewModel::setBackgroundPlaybackEnabled,
                                onRedeemActivationKey = viewModel::redeemActivationKey,
                                onGenerateActivationKey = viewModel::generateNewAccessKey,
                                onToggleKeyRevocation = viewModel::toggleKeyRevocation,
                                onSubmitFeedback = viewModel::submitUserFeedback,
                                onSignOut = {
                                    StreamPlaybackService.stop(context)
                                    signOutUser(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBackgroundPlaybackBar(
    state: BackgroundPlaybackState,
    onTogglePause: () -> Unit,
    onStop: () -> Unit
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(1.dp, SignalCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = SignalCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = state.streamTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${if (state.paused) "Paused" else strings.playingInBackground} • ${state.qualityLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row {
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.testTag("mini_bg_toggle_pause")
                ) {
                    Icon(
                        imageVector = if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (state.paused) "Resume playback" else "Pause playback"
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.testTag("mini_bg_stop")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.stopPlayback,
                        tint = LiveCrimson
                    )
                }
            }
        }
    }
}
