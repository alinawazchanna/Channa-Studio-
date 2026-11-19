package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class StreamResolution(
    val code: String,
    val label: String,
    val width: Int,
    val height: Int,
    val targetBitrateKbps: Int,
    val fps: Int
) {
    SD_480P("480p", "480p SD", 854, 480, 1500, 30),
    HD_720P("720p", "720p HD", 1280, 720, 3500, 30),
    FHD_1080P("1080p", "1080p FHD", 1920, 1080, 6000, 60);

    companion object {
        fun fromCode(raw: String?): StreamResolution =
            entries.firstOrNull { it.code == raw } ?: HD_720P
    }
}

enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromCode(raw: String?): ThemeMode =
            entries.firstOrNull { it.code == raw } ?: DARK
    }
}

enum class StreamSourceType(val code: String) {
    CAMERA("camera"),
    GALLERY("gallery");

    companion object {
        fun fromCode(raw: String?): StreamSourceType =
            entries.firstOrNull { it.code == raw } ?: CAMERA
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Creator",
    val channelName: String = "StreamPulse Live",
    val email: String = "",
    val bio: String = "Streaming live in HD with StreamPulse.",
    val photoUrl: String? = null,
    val preferredLanguage: String = "en",
    val darkModePreference: String = "dark",
    val accessTier: String = "standard",
    val activeKeyCode: String? = null,
    val notificationsEnabled: Boolean = true,
    val backgroundPlaybackEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StreamConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "YouTube Primary",
    val rtmpUrl: String = "rtmps://a.rtmps.youtube.com/live2",
    val streamKey: String = "",
    val wsRelayUrl: String = "wss://ws.postman-echo.com/raw",
    val resolution: String = "720p",
    val lowLatencyEnabled: Boolean = true,
    val audioBitrateKbps: Long = 128L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveStream(
    @DocumentId val id: String = "",
    val hostUserId: String = "",
    val hostName: String = "Creator",
    val title: String = "Live Broadcast",
    val category: String = "General",
    val visibility: String = "public",
    val resolution: String = "720p",
    val sourceType: String = "camera",
    val status: String = "live",
    val lowLatencyWs: Boolean = true,
    val viewerCount: Long = 1L,
    val peakViewers: Long = 1L,
    val totalDonationsInr: Long = 0L,
    val durationSeconds: Long = 0L,
    val bitrateKbps: Long = 3500L,
    val latencyMs: Long = 120L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StreamComment(
    @DocumentId val id: String = "",
    val streamId: String = "",
    val authorId: String = "",
    val authorName: String = "Viewer",
    val visibility: String = "public",
    val message: String = "",
    val messageType: String = "chat",
    val donationAmount: Long = 0L,
    val currency: String = "INR",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivationKey(
    @DocumentId val id: String = "",
    val userId: String = "",
    val keyCode: String = "",
    val tier: String = "pro",
    val status: String = "active",
    val label: String = "Studio Access Key",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val body: String = "",
    val category: String = "system",
    val readFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserFeedback(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "streaming",
    val rating: Long = 5L,
    val comment: String = "",
    val appVersion: String = "1.0",
    @ServerTimestamp val createdAt: Timestamp? = null
)
