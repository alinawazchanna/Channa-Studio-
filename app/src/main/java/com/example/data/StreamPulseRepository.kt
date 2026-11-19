package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StreamPulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        photoUrl: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName.trim().ifBlank { "Creator" }.take(60)
                val safeChannel = "${safeName.split(" ").firstOrNull() ?: "Creator"} Live".take(60)
                val payload = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "channelName" to safeChannel,
                    "email" to email.trim().take(120),
                    "bio" to "Streaming live with StreamPulse HD Studio.",
                    "photoUrl" to photoUrl?.take(500),
                    "preferredLanguage" to "en",
                    "darkModePreference" to "dark",
                    "accessTier" to "standard",
                    "notificationsEnabled" to true,
                    "backgroundPlaybackEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        displayName: String,
        channelName: String,
        bio: String
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val safeDisplay = displayName.trim().take(60)
            val safeChannel = channelName.trim().take(60)
            val safeBio = bio.trim().take(240)
            require(safeDisplay.isNotEmpty()) { "Display name is required" }
            require(safeChannel.isNotEmpty()) { "Channel name is required" }
            db.collection("users").document(uid).update(
                mapOf(
                    "displayName" to safeDisplay,
                    "channelName" to safeChannel,
                    "bio" to safeBio,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserPreferences(
        preferredLanguage: String? = null,
        darkModePreference: String? = null,
        notificationsEnabled: Boolean? = null,
        backgroundPlaybackEnabled: Boolean? = null
    ): Result<Unit> {
        val path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            preferredLanguage?.let { updates["preferredLanguage"] = it }
            darkModePreference?.let { updates["darkModePreference"] = it }
            notificationsEnabled?.let { updates["notificationsEnabled"] = it }
            backgroundPlaybackEnabled?.let { updates["backgroundPlaybackEnabled"] = it }
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- PRIVATE STREAM RTMP / WEBSOCKET CONFIGS ---

    fun observeStreamConfigs(userId: String): Flow<List<StreamConfig>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/streamConfigs"
        emitAll(
            db.collection("users").document(userId).collection("streamConfigs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StreamConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveStreamConfig(
        configId: String = "youtube_primary",
        label: String,
        rtmpUrl: String,
        streamKey: String,
        wsRelayUrl: String,
        resolution: StreamResolution,
        lowLatencyEnabled: Boolean,
        audioBitrateKbps: Long = 128L
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val safeId = configId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "youtube_primary" }
        val path = "users/$uid/streamConfigs/$safeId"
        return try {
            val safeLabel = label.trim().ifBlank { "YouTube Primary" }.take(60)
            val safeRtmp = rtmpUrl.trim().ifBlank { "rtmps://a.rtmps.youtube.com/live2" }.take(300)
            val safeKey = streamKey.trim().take(200)
            val safeWs = wsRelayUrl.trim().take(300)
            val docRef = db.collection("users").document(uid)
                .collection("streamConfigs").document(safeId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "label" to safeLabel,
                        "rtmpUrl" to safeRtmp,
                        "streamKey" to safeKey,
                        "wsRelayUrl" to safeWs,
                        "resolution" to resolution.code,
                        "lowLatencyEnabled" to lowLatencyEnabled,
                        "audioBitrateKbps" to audioBitrateKbps.coerceIn(64L, 320L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "label" to safeLabel,
                        "rtmpUrl" to safeRtmp,
                        "streamKey" to safeKey,
                        "wsRelayUrl" to safeWs,
                        "resolution" to resolution.code,
                        "lowLatencyEnabled" to lowLatencyEnabled,
                        "audioBitrateKbps" to audioBitrateKbps.coerceIn(64L, 320L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- LIVE STREAMS & ANALYTICS ---

    fun observePublicStreams(currentUserId: String): Flow<List<LiveStream>> = flow {
        if (currentUserId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "streams"
        emitAll(
            db.collection("streams")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LiveStream::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareByDescending<LiveStream> { it.status == "live" }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, currentUserId)
                    throw error
                }
        )
    }

    fun observeCreatorStreams(userId: String): Flow<List<LiveStream>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "streams"
        emitAll(
            db.collection("streams")
                .whereEqualTo("hostUserId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LiveStream::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun createLiveStream(
        hostName: String,
        title: String,
        category: String,
        resolution: StreamResolution,
        sourceType: StreamSourceType,
        lowLatencyWs: Boolean,
        latencyMs: Long
    ): Result<String> {
        val streamId = "live_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "streams/$streamId"
        return try {
            val uid = requireUserId()
            val safeTitle = title.trim().ifBlank { "Live Studio Broadcast" }.take(100)
            val safeHost = hostName.trim().ifBlank { "Creator" }.take(60)
            val safeCategory = category.trim().ifBlank { "General" }.take(40)
            val payload = mapOf(
                "hostUserId" to uid,
                "hostName" to safeHost,
                "title" to if (safeTitle.length < 2) "Live Stream" else safeTitle,
                "category" to safeCategory,
                "visibility" to "public",
                "resolution" to resolution.code,
                "sourceType" to sourceType.code,
                "status" to "live",
                "lowLatencyWs" to lowLatencyWs,
                "viewerCount" to 1L,
                "peakViewers" to 1L,
                "totalDonationsInr" to 0L,
                "durationSeconds" to 0L,
                "bitrateKbps" to resolution.targetBitrateKbps.toLong(),
                "latencyMs" to latencyMs.coerceIn(0L, 60000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("streams").document(streamId).set(payload).await()
            Result.success(streamId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateLiveStreamTelemetry(
        streamId: String,
        resolution: StreamResolution,
        durationSeconds: Long,
        bitrateKbps: Long,
        latencyMs: Long,
        viewerCount: Long,
        peakViewers: Long,
        lowLatencyWs: Boolean
    ): Result<Unit> {
        val path = "streams/$streamId"
        return try {
            requireUserId()
            db.collection("streams").document(streamId).update(
                mapOf(
                    "resolution" to resolution.code,
                    "durationSeconds" to durationSeconds.coerceIn(0L, 864000L),
                    "bitrateKbps" to bitrateKbps.coerceIn(500L, 25000L),
                    "latencyMs" to latencyMs.coerceIn(0L, 60000L),
                    "viewerCount" to viewerCount.coerceIn(0L, 10000000L),
                    "peakViewers" to peakViewers.coerceIn(0L, 10000000L),
                    "lowLatencyWs" to lowLatencyWs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun endLiveStream(
        streamId: String,
        durationSeconds: Long,
        peakViewers: Long
    ): Result<Unit> {
        val path = "streams/$streamId"
        return try {
            requireUserId()
            db.collection("streams").document(streamId).update(
                mapOf(
                    "status" to "ended",
                    "viewerCount" to 0L,
                    "peakViewers" to peakViewers.coerceIn(0L, 10000000L),
                    "durationSeconds" to durationSeconds.coerceIn(0L, 864000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- REAL-TIME COMMENTS & SUPERCHAT DONATIONS ---

    fun observeStreamComments(streamId: String, currentUserId: String): Flow<List<StreamComment>> = flow {
        if (streamId.isBlank() || currentUserId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "streams/$streamId/comments"
        emitAll(
            db.collection("streams").document(streamId).collection("comments")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StreamComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, currentUserId)
                    throw error
                }
        )
    }

    suspend fun postCommentOrDonation(
        streamId: String,
        authorName: String,
        message: String,
        donationAmountInr: Long = 0L,
        currentStreamTotalDonations: Long = 0L,
        currentStreamViewers: Long = 1L,
        currentStreamPeakViewers: Long = 1L,
        streamIsLive: Boolean = true
    ): Result<Unit> {
        val commentId = "msg_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val path = "streams/$streamId/comments/$commentId"
        return try {
            val uid = requireUserId()
            val safeMessage = message.trim().take(300)
            require(safeMessage.isNotEmpty()) { "Message cannot be empty" }
            val isDonation = donationAmountInr >= 10L
            val safeAmount = if (isDonation) donationAmountInr.coerceIn(10L, 100000L) else 0L
            val safeAuthor = authorName.trim().ifBlank { "Viewer" }.take(60)

            val payload = mapOf(
                "streamId" to streamId,
                "authorId" to uid,
                "authorName" to safeAuthor,
                "visibility" to "public",
                "message" to safeMessage,
                "messageType" to if (isDonation) "donation" else "chat",
                "donationAmount" to safeAmount,
                "currency" to "INR",
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("streams").document(streamId)
                .collection("comments").document(commentId)
                .set(payload)
                .await()

            if (isDonation && streamIsLive) {
                val updatedTotal = currentStreamTotalDonations + safeAmount
                val updatedPeak = maxOf(currentStreamPeakViewers, currentStreamViewers)
                db.collection("streams").document(streamId).update(
                    mapOf(
                        "totalDonationsInr" to updatedTotal,
                        "viewerCount" to currentStreamViewers,
                        "peakViewers" to updatedPeak,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- ACTIVATION KEYS SYSTEM ---

    fun observeActivationKeys(userId: String): Flow<List<ActivationKey>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/activationKeys"
        emitAll(
            db.collection("users").document(userId).collection("activationKeys")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ActivationKey::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun activateLicenseKey(
        rawKeyCode: String,
        tier: String,
        label: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val cleanCode = rawKeyCode.trim().uppercase().take(40)
        val keyId = "key_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val path = "users/$uid/activationKeys/$keyId"
        return try {
            require(cleanCode.length >= 6) { "Activation key must be at least 6 characters" }
            val safeTier = if (tier == "creator") "creator" else "pro"
            val safeLabel = label.trim().ifBlank { "Studio Access Key" }.take(80)
            db.collection("users").document(uid).collection("activationKeys").document(keyId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "keyCode" to cleanCode,
                        "tier" to safeTier,
                        "status" to "active",
                        "label" to safeLabel,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            db.collection("users").document(uid).update(
                mapOf(
                    "accessTier" to safeTier,
                    "activeKeyCode" to cleanCode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleActivationKeyStatus(
        keyId: String,
        newStatus: String,
        fallbackTier: String,
        fallbackKeyCode: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/activationKeys/$keyId"
        return try {
            val safeStatus = if (newStatus == "revoked") "revoked" else "active"
            db.collection("users").document(uid).collection("activationKeys").document(keyId)
                .update(
                    mapOf(
                        "status" to safeStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            val userUpdates = mutableMapOf<String, Any?>(
                "accessTier" to fallbackTier,
                "activeKeyCode" to fallbackKeyCode,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(userUpdates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- NOTIFICATIONS ---

    fun observeNotifications(userId: String): Flow<List<AppNotification>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/notifications"
        emitAll(
            db.collection("users").document(userId).collection("notifications")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AppNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun recordNotification(
        title: String,
        body: String,
        category: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val path = "users/$uid/notifications/$notifId"
        return try {
            val safeCat = if (category in setOf("live", "donation", "system", "activation")) category else "system"
            db.collection("users").document(uid).collection("notifications").document(notifId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "title" to title.trim().ifBlank { "StreamPulse Alert" }.take(100),
                        "body" to body.trim().ifBlank { "Studio notification received." }.take(300),
                        "category" to safeCat,
                        "readFlag" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val path = "users/$uid/notifications/$notifId"
        return try {
            db.collection("users").document(uid).collection("notifications").document(notifId)
                .update(mapOf("readFlag" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- USER FEEDBACK ---

    fun observeFeedback(userId: String): Flow<List<UserFeedback>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/feedback"
        emitAll(
            db.collection("users").document(userId).collection("feedback")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserFeedback::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun submitFeedback(
        category: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Not authenticated"))
        val fbId = "fb_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val path = "users/$uid/feedback/$fbId"
        return try {
            val safeComment = comment.trim().take(600)
            require(safeComment.length >= 3) { "Please enter at least 3 characters of feedback" }
            val safeCategory = if (category in setOf("streaming", "audio_video", "ui_ux", "feature", "bug")) category else "streaming"
            db.collection("users").document(uid).collection("feedback").document(fbId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "category" to safeCategory,
                        "rating" to rating.coerceIn(1, 5).toLong(),
                        "comment" to safeComment,
                        "appVersion" to "1.0.0",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
