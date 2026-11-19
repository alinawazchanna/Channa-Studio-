package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ActivationKey
import com.example.data.AppNotification
import com.example.data.LocalAppPreferences
import com.example.data.ThemeMode
import com.example.data.UiState
import com.example.data.UserFeedback
import com.example.data.UserProfile
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.SuperChatGold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsScreen(
    userProfileState: UiState<UserProfile?>,
    preferences: LocalAppPreferences,
    activationKeysState: UiState<List<ActivationKey>>,
    notificationsState: UiState<List<AppNotification>>,
    feedbackState: UiState<List<UserFeedback>>,
    onSaveProfile: (String, String, String) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSendTestAlert: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onToggleBackgroundPlaybackPref: (Boolean) -> Unit,
    onRedeemActivationKey: (String) -> Unit,
    onGenerateActivationKey: () -> Unit,
    onToggleKeyRevocation: (ActivationKey) -> Unit,
    onSubmitFeedback: (String, Int, String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val profile = (userProfileState as? UiState.Success)?.data
    val keys = (activationKeysState as? UiState.Success)?.data.orEmpty()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val feedbackList = (feedbackState as? UiState.Success)?.data.orEmpty()

    var displayNameDraft by remember(profile?.displayName) { mutableStateOf(profile?.displayName ?: "Creator") }
    var channelNameDraft by remember(profile?.channelName) { mutableStateOf(profile?.channelName ?: "StreamPulse Live") }
    var bioDraft by remember(profile?.bio) { mutableStateOf(profile?.bio ?: "") }

    var activationKeyInput by remember { mutableStateOf("") }
    var feedbackCategory by remember { mutableStateOf("streaming") }
    var feedbackRating by remember { mutableIntStateOf(5) }
    var feedbackComment by remember { mutableStateOf("") }

    var showLegalDialog by remember { mutableStateOf<String?>(null) }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onToggleNotifications(granted)
        if (granted) onSendTestAlert()
    }

    if (showLegalDialog != null) {
        LegalInfoDialog(
            section = showLegalDialog ?: "privacy",
            onDismiss = { showLegalDialog = null },
            onOpenExternalUrl = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
    ) {
        // Header + Sign Out
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.profileTitle,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = profile?.email.orEmpty().ifBlank { "Google Account Connected" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.signOut)
                }
            }
        }

        // 1. User Profile Management Card
        item {
            SettingsCard(
                icon = Icons.Filled.Person,
                iconTint = SignalCyan,
                title = strings.editProfile,
                badgeText = if (profile?.accessTier == "pro" || profile?.accessTier == "creator") {
                    strings.activeLicenseBadge
                } else strings.standardTierBadge,
                badgeColor = if (profile?.accessTier == "pro" || profile?.accessTier == "creator") {
                    StudioEmerald
                } else SuperChatGold
            ) {
                OutlinedTextField(
                    value = displayNameDraft,
                    onValueChange = { displayNameDraft = it.take(60) },
                    label = { Text(strings.displayName) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_display_name_input")
                )
                OutlinedTextField(
                    value = channelNameDraft,
                    onValueChange = { channelNameDraft = it.take(60) },
                    label = { Text(strings.channelName) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_channel_name_input")
                )
                OutlinedTextField(
                    value = bioDraft,
                    onValueChange = { bioDraft = it.take(240) },
                    label = { Text(strings.bioLabel) },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_bio_input")
                )
                Button(
                    onClick = { onSaveProfile(displayNameDraft, channelNameDraft, bioDraft) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_profile_button")
                ) {
                    Text(strings.saveProfile)
                }
            }
        }

        // 2. Activation Key & Access Management System
        item {
            SettingsCard(
                icon = Icons.Filled.Key,
                iconTint = SuperChatGold,
                title = strings.activationKeyTitle
            ) {
                Text(
                    text = "Manage studio access tiers with activation keys. Tap a starter key below or generate your own revocable studio key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("PULSE-PRO-2026", "STREAM-HD-1080", "CREATOR-VIP-786").forEach { demoKey ->
                        FilterChip(
                            selected = activationKeyInput == demoKey,
                            onClick = { activationKeyInput = demoKey },
                            label = { Text(demoKey, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = activationKeyInput,
                    onValueChange = { activationKeyInput = it.uppercase().take(40) },
                    label = { Text(strings.activationKeyHint) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("activation_key_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onRedeemActivationKey(activationKeyInput)
                            activationKeyInput = ""
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuperChatGold,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("activate_key_button")
                    ) {
                        Text(strings.activateKeyButton, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onGenerateActivationKey,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("generate_key_button")
                    ) {
                        Text(strings.generateKeyButton)
                    }
                }

                if (keys.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.take(5).forEach { key ->
                            val active = key.status == "active"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Verified,
                                                contentDescription = null,
                                                tint = if (active) StudioEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = key.keyCode,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                        Text(
                                            text = "${key.tier.uppercase()} • ${key.status.uppercase()}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (active) StudioEmerald else LiveCrimson
                                        )
                                    }
                                    TextButton(onClick = { onToggleKeyRevocation(key) }) {
                                        Text(if (active) "Revoke" else "Reactivate")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Appearance (Dark/Light Mode) & Multi-Language Selector
        item {
            SettingsCard(
                icon = Icons.Filled.DarkMode,
                iconTint = SignalCyan,
                title = "${strings.appearance} & ${strings.languageLabel}"
            ) {
                Text(strings.appearance, style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ThemeMode.DARK to strings.darkMode,
                        ThemeMode.LIGHT to strings.lightMode,
                        ThemeMode.SYSTEM to strings.systemMode
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onSelectTheme(mode) },
                            label = { Text(label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_${mode.code}_chip")
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = SignalCyan, modifier = Modifier.size(18.dp))
                    Text(strings.languageLabel, style = MaterialTheme.typography.labelLarge)
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = preferences.language == lang,
                            onClick = { onSelectLanguage(lang) },
                            label = { Text(lang.nativeLabel) },
                            modifier = Modifier.testTag("lang_${lang.code}_chip")
                        )
                    }
                }

                // Background Playback Preference
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Headphones, contentDescription = null, tint = SignalCyan)
                        Column {
                            Text(strings.backgroundPlayback, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Keep stream audio playing in foreground service",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = preferences.backgroundPlaybackEnabled,
                        onCheckedChange = onToggleBackgroundPlaybackPref,
                        modifier = Modifier.testTag("bg_playback_pref_switch")
                    )
                }
            }
        }

        // 4. Push Notifications & Firebase Alert Inbox
        item {
            SettingsCard(
                icon = Icons.Filled.NotificationsActive,
                iconTint = LiveCrimson,
                title = strings.notificationsTitle
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.enablePushAlerts, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Real-time donation & go-live alerts synced with Firebase. Enable FCM in Firebase Console for remote cloud campaigns.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onToggleNotifications(enabled)
                            }
                        },
                        modifier = Modifier.testTag("push_alerts_switch")
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSendTestAlert()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("send_test_notification_button")
                ) {
                    Text(strings.testNotification)
                }

                if (notifications.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notifications.take(4).forEach { notif ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (notif.readFlag) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                } else {
                                    LiveCrimson.copy(alpha = 0.12f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!notif.readFlag) onMarkNotificationRead(notif.id)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = notif.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!notif.readFlag) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(LiveCrimson)
                                            )
                                        }
                                    }
                                    Text(
                                        text = notif.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. User Feedback System
        item {
            SettingsCard(
                icon = Icons.Filled.Feedback,
                iconTint = StudioEmerald,
                title = strings.feedbackTitle
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "streaming" to "Streaming",
                        "audio_video" to "Audio/Video",
                        "ui_ux" to "UI/UX",
                        "feature" to "Feature",
                        "bug" to "Bug"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = feedbackCategory == code,
                            onClick = { feedbackCategory = code },
                            label = { Text(label) }
                        )
                    }
                }

                // 5-Star Rating Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { feedbackRating = star },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (star <= feedbackRating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "$star stars",
                                tint = SuperChatGold,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Text(
                        text = "$feedbackRating / 5",
                        style = MaterialTheme.typography.labelLarge,
                        color = SuperChatGold
                    )
                }

                OutlinedTextField(
                    value = feedbackComment,
                    onValueChange = { feedbackComment = it.take(600) },
                    label = { Text(strings.feedbackHint) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("feedback_comment_input")
                )

                Button(
                    onClick = {
                        onSubmitFeedback(feedbackCategory, feedbackRating, feedbackComment)
                        if (feedbackComment.trim().length >= 3) feedbackComment = ""
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_feedback_button")
                ) {
                    Text(strings.submitFeedback)
                }

                if (feedbackList.isNotEmpty()) {
                    Text(
                        text = "Your recent submissions (${feedbackList.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    feedbackList.take(2).forEach { fb ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "★${fb.rating} [${fb.category}] ${fb.comment}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 6. About Us & Privacy Policy Links
        item {
            SettingsCard(
                icon = Icons.Filled.Info,
                iconTint = SignalCyan,
                title = strings.aboutUsTitle
            ) {
                Text(
                    text = "StreamPulse v1.0.0 — Built for mobile & tablet creators who want YouTube RTMP streaming, low-latency WebSocket telemetry, gallery video broadcasts, real-time chat & donations, and continuous background playback.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showLegalDialog = "privacy" },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("privacy_policy_button")
                    ) {
                        Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.privacyPolicy)
                    }
                    OutlinedButton(
                        onClick = { showLegalDialog = "about" },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("terms_about_button")
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.termsOfService)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    badgeText: String? = null,
    badgeColor: Color = StudioEmerald,
    content: @Composable () -> Unit
) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                if (badgeText != null) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun LegalInfoDialog(
    section: String,
    onDismiss: () -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val isPrivacy = section == "privacy"
    val externalUrl = if (isPrivacy) {
        "https://policies.google.com/privacy"
    } else {
        "https://policies.google.com/terms"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPrivacy) "StreamPulse Privacy Policy" else "About Us & Terms of Service") },
        text = {
            Text(
                text = if (isPrivacy) {
                    "StreamPulse respects your privacy:\n\n" +
                        "1. Private RTMP Stream Keys & Credentials are stored strictly in owner-isolated Firestore paths and never exposed to viewers.\n" +
                        "2. Gallery Videos are accessed using Android's zero-permission Photo Picker without broad storage access.\n" +
                        "3. Camera & Microphone permissions are used solely during active studio preview or broadcast.\n" +
                        "4. You may revoke activation keys or sign out at any time."
                } else {
                    "StreamPulse Live Studio empowers creators to broadcast to YouTube via RTMP/RTMPS with low-latency WebSocket monitoring, real-time viewer interaction, and background audio continuity.\n\n" +
                        "By using StreamPulse, you agree to comply with YouTube Community Guidelines and broadcast only authorized content."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = { onOpenExternalUrl(externalUrl) }) {
                Text("Open Policy Link")
            }
        }
    )
}
