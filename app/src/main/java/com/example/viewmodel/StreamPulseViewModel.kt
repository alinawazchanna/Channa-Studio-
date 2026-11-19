package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ActivationKey
import com.example.data.AppNotification
import com.example.data.LiveStream
import com.example.data.LocalAppPreferences
import com.example.data.PreferencesRepository
import com.example.data.StreamComment
import com.example.data.StreamConfig
import com.example.data.StreamPulseRepository
import com.example.data.StreamResolution
import com.example.data.StreamSourceType
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.data.UserFeedback
import com.example.data.UserProfile
import com.example.i18n.AppLanguage
import com.example.network.LowLatencyWebSocketClient
import com.example.network.StreamTransportTelemetry
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class StudioSessionState(
    val liveActive: Boolean = false,
    val activeStreamId: String? = null,
    val streamTitle: String = "My YouTube Live Session",
    val streamCategory: String = "Gaming & Tech",
    val resolution: StreamResolution = StreamResolution.HD_720P,
    val sourceType: StreamSourceType = StreamSourceType.CAMERA,
    val galleryVideoUri: String? = null,
    val micMuted: Boolean = false,
    val frontCamera: Boolean = true,
    val lowLatencyEnabled: Boolean = true,
    val rtmpUrl: String = "rtmps://a.rtmps.youtube.com/live2",
    val streamKey: String = "",
    val wsRelayUrl: String = "wss://ws.postman-echo.com/raw",
    val elapsedSeconds: Long = 0L,
    val estimatedViewers: Long = 1L,
    val peakViewers: Long = 1L,
    val busy: Boolean = false
)

data class PushAlertEvent(
    val title: String,
    val body: String
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StreamPulseViewModel(
    private val repository: StreamPulseRepository,
    private val preferencesRepository: PreferencesRepository,
    val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String,
    private val initialPhotoUrl: String?
) : ViewModel() {

    private val wsClient = LowLatencyWebSocketClient()
    val wsTelemetry: StateFlow<StreamTransportTelemetry> = wsClient.telemetry

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val _pushAlertEvents = MutableSharedFlow<PushAlertEvent>(extraBufferCapacity = 8)
    val pushAlertEvents: SharedFlow<PushAlertEvent> = _pushAlertEvents.asSharedFlow()

    private val _studioState = MutableStateFlow(StudioSessionState())
    val studioState: StateFlow<StudioSessionState> = _studioState.asStateFlow()

    private val _selectedWatchStreamId = MutableStateFlow<String?>(null)
    val selectedWatchStreamId: StateFlow<String?> = _selectedWatchStreamId.asStateFlow()

    val localPreferences: StateFlow<LocalAppPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = LocalAppPreferences()
        )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId).toUiStateFlow("Failed to load profile")

    val streamConfigsState: StateFlow<UiState<List<StreamConfig>>> =
        repository.observeStreamConfigs(currentUserId).toUiStateFlow("Failed to load stream config")

    val publicStreamsState: StateFlow<UiState<List<LiveStream>>> =
        repository.observePublicStreams(currentUserId).toUiStateFlow("Failed to load live streams")

    val creatorStreamsState: StateFlow<UiState<List<LiveStream>>> =
        repository.observeCreatorStreams(currentUserId).toUiStateFlow("Failed to load analytics")

    val activationKeysState: StateFlow<UiState<List<ActivationKey>>> =
        repository.observeActivationKeys(currentUserId).toUiStateFlow("Failed to load activation keys")

    val notificationsState: StateFlow<UiState<List<AppNotification>>> =
        repository.observeNotifications(currentUserId).toUiStateFlow("Failed to load notifications")

    val feedbackState: StateFlow<UiState<List<UserFeedback>>> =
        repository.observeFeedback(currentUserId).toUiStateFlow("Failed to load feedback")

    // Observes comments for whichever stream is open in Watch Room OR active in Studio
    val activeCommentsState: StateFlow<UiState<List<StreamComment>>> =
        _selectedWatchStreamId
            .flatMapLatest { watchId ->
                val targetId = watchId ?: _studioState.value.activeStreamId
                if (targetId.isNullOrBlank()) {
                    flowOf<UiState<List<StreamComment>>>(UiState.Success(emptyList()))
                } else {
                    repository.observeStreamComments(targetId, currentUserId)
                        .map<List<StreamComment>, UiState<List<StreamComment>>> { UiState.Success(it) }
                        .catch { error -> emit(handleStreamException(error, "Failed to load live chat")) }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Success(emptyList())
            )

    private var broadcastTimerJob: Job? = null
    private var configHydrated = false

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName, initialEmail, initialPhotoUrl)
        }
        viewModelScope.launch {
            streamConfigsState.collect { state ->
                if (state is UiState.Success && !configHydrated) {
                    val primary = state.data.firstOrNull()
                    if (primary != null) {
                        configHydrated = true
                        _studioState.update {
                            it.copy(
                                rtmpUrl = primary.rtmpUrl,
                                streamKey = primary.streamKey,
                                wsRelayUrl = primary.wsRelayUrl,
                                resolution = StreamResolution.fromCode(primary.resolution),
                                lowLatencyEnabled = primary.lowLatencyEnabled
                            )
                        }
                    }
                }
            }
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(fallbackMessage: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error -> emit(handleStreamException(error, fallbackMessage)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    private fun handleStreamException(error: Throwable, fallbackMessage: String): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallbackMessage, rootError)
        return UiState.Error(rootError.message ?: fallbackMessage)
    }

    // --- STUDIO CONTROLS ---

    fun updateStudioTitle(title: String) {
        _studioState.update { it.copy(streamTitle = title.take(100)) }
    }

    fun updateStudioCategory(category: String) {
        _studioState.update { it.copy(streamCategory = category.take(40)) }
    }

    fun updateSourceType(sourceType: StreamSourceType) {
        _studioState.update { it.copy(sourceType = sourceType) }
    }

    fun setGalleryVideoUri(uri: String?) {
        _studioState.update {
            it.copy(
                galleryVideoUri = uri,
                sourceType = if (uri != null) StreamSourceType.GALLERY else it.sourceType
            )
        }
    }

    fun toggleMicMute() {
        _studioState.update { it.copy(micMuted = !it.micMuted) }
    }

    fun toggleCameraFacing() {
        _studioState.update { it.copy(frontCamera = !it.frontCamera) }
    }

    fun setResolution(resolution: StreamResolution) {
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        if (resolution == StreamResolution.FHD_1080P && currentProfile?.accessTier == "standard") {
            _snackbarMessages.tryEmit("Tip: Activate key PULSE-PRO-2026 in Profile for unlocked Pro 1080p badge (preview enabled)")
        }
        _studioState.update { it.copy(resolution = resolution) }
        viewModelScope.launch {
            preferencesRepository.setResolution(resolution)
            val streamId = _studioState.value.activeStreamId
            if (_studioState.value.liveActive && streamId != null) {
                wsClient.sendControlPacket(
                    type = "resolution_change",
                    streamId = streamId,
                    payload = mapOf(
                        "resolution" to resolution.code,
                        "bitrateKbps" to resolution.targetBitrateKbps
                    )
                )
            }
        }
    }

    fun updateRtmpAndWsDraft(
        rtmpUrl: String,
        streamKey: String,
        wsRelayUrl: String,
        lowLatencyEnabled: Boolean
    ) {
        _studioState.update {
            it.copy(
                rtmpUrl = rtmpUrl.trim(),
                streamKey = streamKey.trim(),
                wsRelayUrl = wsRelayUrl.trim(),
                lowLatencyEnabled = lowLatencyEnabled
            )
        }
    }

    fun saveStreamConfiguration(
        rtmpUrl: String,
        streamKey: String,
        wsRelayUrl: String,
        lowLatencyEnabled: Boolean
    ) {
        updateRtmpAndWsDraft(rtmpUrl, streamKey, wsRelayUrl, lowLatencyEnabled)
        viewModelScope.launch {
            val res = repository.saveStreamConfig(
                label = "YouTube Live Studio",
                rtmpUrl = rtmpUrl,
                streamKey = streamKey,
                wsRelayUrl = wsRelayUrl,
                resolution = _studioState.value.resolution,
                lowLatencyEnabled = lowLatencyEnabled
            )
            if (res.isSuccess) {
                _snackbarMessages.tryEmit("RTMP & WebSocket configuration saved")
                wsClient.verifyRtmpEndpoint(rtmpUrl)
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Failed to save config")
            }
        }
    }

    fun testRtmpConnection() {
        val url = _studioState.value.rtmpUrl
        viewModelScope.launch {
            val (_, note) = wsClient.verifyRtmpEndpoint(url)
            _snackbarMessages.tryEmit(note)
        }
    }

    fun startBroadcast() {
        val state = _studioState.value
        if (state.liveActive || state.busy) return
        if (state.sourceType == StreamSourceType.GALLERY && state.galleryVideoUri.isNullOrBlank()) {
            _snackbarMessages.tryEmit("Select a video from your phone gallery first")
            return
        }
        val hostName = (userProfileState.value as? UiState.Success)?.data?.channelName
            ?: initialDisplayName.ifBlank { "Creator Live" }

        _studioState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val initialLatency = if (state.lowLatencyEnabled) 95L else 420L
            val result = repository.createLiveStream(
                hostName = hostName,
                title = state.streamTitle,
                category = state.streamCategory,
                resolution = state.resolution,
                sourceType = state.sourceType,
                lowLatencyWs = state.lowLatencyEnabled,
                latencyMs = initialLatency
            )
            result.onSuccess { streamId ->
                _studioState.update {
                    it.copy(
                        liveActive = true,
                        activeStreamId = streamId,
                        elapsedSeconds = 0L,
                        estimatedViewers = 1L,
                        peakViewers = 1L,
                        busy = false
                    )
                }
                _selectedWatchStreamId.value = streamId
                if (state.lowLatencyEnabled) {
                    wsClient.connect(
                        url = state.wsRelayUrl,
                        streamId = streamId,
                        resolutionCode = state.resolution.code,
                        scope = viewModelScope
                    )
                }
                wsClient.verifyRtmpEndpoint(state.rtmpUrl)
                triggerAlert(
                    title = "You're Live on StreamPulse!",
                    body = "${state.streamTitle} (${state.resolution.code}) is now broadcasting.",
                    category = "live"
                )
                startBroadcastTicker(streamId)
            }.onFailure { err ->
                _studioState.update { it.copy(busy = false) }
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to start broadcast")
            }
        }
    }

    private fun startBroadcastTicker(streamId: String) {
        broadcastTimerJob?.cancel()
        broadcastTimerJob = viewModelScope.launch {
            while (isActive && _studioState.value.liveActive) {
                delay(1000L)
                _studioState.update { cur ->
                    val nextElapsed = cur.elapsedSeconds + 1L
                    cur.copy(elapsedSeconds = nextElapsed)
                }
                val cur = _studioState.value
                if (cur.elapsedSeconds % 10L == 0L) {
                    val liveLatency = wsClient.telemetry.value.latencyMs.takeIf { it > 0L }
                        ?: if (cur.lowLatencyEnabled) 110L else 450L
                    repository.updateLiveStreamTelemetry(
                        streamId = streamId,
                        resolution = cur.resolution,
                        durationSeconds = cur.elapsedSeconds,
                        bitrateKbps = cur.resolution.targetBitrateKbps.toLong(),
                        latencyMs = liveLatency,
                        viewerCount = cur.estimatedViewers,
                        peakViewers = cur.peakViewers,
                        lowLatencyWs = cur.lowLatencyEnabled
                    )
                }
            }
        }
    }

    fun endBroadcast() {
        val state = _studioState.value
        val streamId = state.activeStreamId ?: return
        broadcastTimerJob?.cancel()
        wsClient.disconnect()
        _studioState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = repository.endLiveStream(
                streamId = streamId,
                durationSeconds = state.elapsedSeconds,
                peakViewers = state.peakViewers
            )
            _studioState.update {
                it.copy(
                    liveActive = false,
                    activeStreamId = null,
                    elapsedSeconds = 0L,
                    busy = false
                )
            }
            if (res.isSuccess) {
                _snackbarMessages.tryEmit("Broadcast ended & saved to Analytics")
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Error ending stream")
            }
        }
    }

    // --- WATCH ROOM, REAL-TIME COMMENTS & DONATIONS ---

    fun selectStreamToWatch(streamId: String?) {
        _selectedWatchStreamId.value = streamId
    }

    fun sendLiveComment(stream: LiveStream, message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        val authorName = (userProfileState.value as? UiState.Success)?.data?.displayName
            ?: initialDisplayName.ifBlank { "Viewer" }

        viewModelScope.launch {
            val res = repository.postCommentOrDonation(
                streamId = stream.id,
                authorName = authorName,
                message = trimmed,
                donationAmountInr = 0L,
                currentStreamTotalDonations = stream.totalDonationsInr,
                currentStreamViewers = stream.viewerCount,
                currentStreamPeakViewers = stream.peakViewers,
                streamIsLive = stream.status == "live"
            )
            if (res.isSuccess) {
                wsClient.sendControlPacket(
                    type = "chat",
                    streamId = stream.id,
                    payload = mapOf("author" to authorName, "message" to trimmed)
                )
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Could not post comment")
            }
        }
    }

    fun sendSuperChatDonation(stream: LiveStream, amountInr: Long, note: String) {
        if (amountInr < 10L) {
            _snackbarMessages.tryEmit("Minimum SuperChat donation is ₹10")
            return
        }
        val authorName = (userProfileState.value as? UiState.Success)?.data?.displayName
            ?: initialDisplayName.ifBlank { "Supporter" }
        val finalNote = note.trim().ifBlank { "SuperChat ₹$amountInr support!" }

        viewModelScope.launch {
            val res = repository.postCommentOrDonation(
                streamId = stream.id,
                authorName = authorName,
                message = finalNote,
                donationAmountInr = amountInr,
                currentStreamTotalDonations = stream.totalDonationsInr,
                currentStreamViewers = maxOf(1L, stream.viewerCount),
                currentStreamPeakViewers = maxOf(1L, stream.peakViewers),
                streamIsLive = stream.status == "live"
            )
            if (res.isSuccess) {
                wsClient.sendControlPacket(
                    type = "donation",
                    streamId = stream.id,
                    payload = mapOf("author" to authorName, "amountInr" to amountInr)
                )
                triggerAlert(
                    title = "SuperChat ₹$amountInr Received!",
                    body = "$authorName: $finalNote",
                    category = "donation"
                )
                _snackbarMessages.tryEmit("SuperChat ₹$amountInr sent!")
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Donation failed")
            }
        }
    }

    // --- PROFILE, THEME, MULTI-LANGUAGE & PREFERENCES ---

    fun saveProfile(displayName: String, channelName: String, bio: String) {
        viewModelScope.launch {
            val res = repository.updateUserProfile(displayName, channelName, bio)
            if (res.isSuccess) {
                _snackbarMessages.tryEmit("Profile updated")
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Profile update failed")
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            repository.updateUserPreferences(darkModePreference = mode.code)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            preferencesRepository.setLanguage(language)
            repository.updateUserPreferences(preferredLanguage = language.code)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
            repository.updateUserPreferences(notificationsEnabled = enabled)
        }
    }

    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundPlayback(enabled)
            repository.updateUserPreferences(backgroundPlaybackEnabled = enabled)
        }
    }

    // --- ACTIVATION KEY ACCESS SYSTEM ---

    fun redeemActivationKey(rawCode: String) {
        val normalized = rawCode.trim().uppercase()
        if (normalized.length < 6) {
            _snackbarMessages.tryEmit("Enter a valid activation key (min 6 characters)")
            return
        }
        val knownProKeys = setOf("PULSE-PRO-2026", "STREAM-HD-1080", "CREATOR-VIP-786")
        val matchesCustomFormat = normalized.startsWith("SP-") && normalized.length >= 10
        if (normalized !in knownProKeys && !matchesCustomFormat) {
            _snackbarMessages.tryEmit("Invalid key. Try PULSE-PRO-2026, STREAM-HD-1080, or generate a new key.")
            return
        }
        val tier = if (normalized.contains("CREATOR") || normalized.contains("VIP")) "creator" else "pro"
        val label = if (tier == "creator") "Creator VIP Studio License" else "StreamPulse Pro Access"
        viewModelScope.launch {
            val res = repository.activateLicenseKey(normalized, tier, label)
            if (res.isSuccess) {
                triggerAlert(
                    title = "Activation Key Unlocked",
                    body = "Key $normalized activated ($tier tier).",
                    category = "activation"
                )
                _snackbarMessages.tryEmit("Access activated: $normalized ($tier)")
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Activation failed")
            }
        }
    }

    fun generateNewAccessKey() {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        val part2 = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        val generated = "SP-$part1-$part2"
        viewModelScope.launch {
            val res = repository.activateLicenseKey(generated, "pro", "Generated Studio Access Key")
            if (res.isSuccess) {
                _snackbarMessages.tryEmit("Generated & activated key: $generated")
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Could not generate key")
            }
        }
    }

    fun toggleKeyRevocation(key: ActivationKey) {
        val nextStatus = if (key.status == "active") "revoked" else "active"
        val keysList = (activationKeysState.value as? UiState.Success)?.data.orEmpty()
        val remainingActive = keysList.firstOrNull {
            if (it.id == key.id) nextStatus == "active" else it.status == "active"
        }
        val nextTier = remainingActive?.tier ?: "standard"
        val nextCode = remainingActive?.keyCode

        viewModelScope.launch {
            val res = repository.toggleActivationKeyStatus(
                keyId = key.id,
                newStatus = nextStatus,
                fallbackTier = nextTier,
                fallbackKeyCode = nextCode
            )
            if (res.isSuccess) {
                _snackbarMessages.tryEmit(
                    if (nextStatus == "revoked") "Key ${key.keyCode} revoked" else "Key ${key.keyCode} reactivated"
                )
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Failed to update key")
            }
        }
    }

    // --- NOTIFICATIONS & FEEDBACK ---

    fun sendTestPushAlert() {
        triggerAlert(
            title = "StreamPulse Live Alerts Ready",
            body = "Push alerts are active for real-time donations, chat spikes, and stream status.",
            category = "system"
        )
        _snackbarMessages.tryEmit("Test alert sent")
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notifId)
        }
    }

    fun submitUserFeedback(category: String, rating: Int, comment: String) {
        if (comment.trim().length < 3) {
            _snackbarMessages.tryEmit("Please write at least 3 characters")
            return
        }
        viewModelScope.launch {
            val res = repository.submitFeedback(category, rating, comment)
            if (res.isSuccess) {
                _snackbarMessages.tryEmit("Thank you! Feedback submitted")
            } else {
                _snackbarMessages.tryEmit(res.exceptionOrNull()?.localizedMessage ?: "Feedback submission failed")
            }
        }
    }

    private fun triggerAlert(title: String, body: String, category: String) {
        viewModelScope.launch {
            repository.recordNotification(title, body, category)
            if (localPreferences.value.notificationsEnabled) {
                _pushAlertEvents.tryEmit(PushAlertEvent(title, body))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        broadcastTimerJob?.cancel()
        wsClient.disconnect()
    }

    private companion object {
        const val TAG = "StreamPulseVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
