package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.LiveStream
import com.example.data.UiState
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.StudioEmerald
import com.example.ui.theme.SuperChatGold

@Composable
fun AnalyticsScreen(
    creatorStreamsState: UiState<List<LiveStream>>,
    isWideScreen: Boolean
) {
    val strings = LocalAppStrings.current

    when (creatorStreamsState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(creatorStreamsState.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is UiState.Success -> {
            val streams = creatorStreamsState.data
            val totalStreams = streams.size
            val peakViewers = streams.maxOfOrNull { it.peakViewers } ?: 0L
            val totalDonations = streams.sumOf { it.totalDonationsInr }
            val avgLatency = if (streams.isNotEmpty()) streams.map { it.latencyMs }.average().toLong() else 0L
            val totalDurationMinutes = streams.sumOf { it.durationSeconds } / 60L

            val resCounts = remember(streams) {
                mapOf(
                    "1080p" to streams.count { it.resolution == "1080p" },
                    "720p" to streams.count { it.resolution == "720p" },
                    "480p" to streams.count { it.resolution == "480p" }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = strings.analyticsTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Real-time telemetry • ${totalDurationMinutes}m total broadcast time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // KPI Cards (4-in-row on Tablet, 2x2 on Phone)
                item {
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AnalyticsKpiCard(Icons.Filled.LiveTv, strings.totalStreams, "$totalStreams", LiveCrimson, Modifier.weight(1f))
                            AnalyticsKpiCard(Icons.Filled.Groups, strings.peakViewers, "$peakViewers", SignalCyan, Modifier.weight(1f))
                            AnalyticsKpiCard(Icons.Filled.VolunteerActivism, strings.totalDonations, "₹$totalDonations", SuperChatGold, Modifier.weight(1f))
                            AnalyticsKpiCard(Icons.Filled.Bolt, strings.avgLatency, "${avgLatency}ms", StudioEmerald, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AnalyticsKpiCard(Icons.Filled.LiveTv, strings.totalStreams, "$totalStreams", LiveCrimson, Modifier.weight(1f))
                                AnalyticsKpiCard(Icons.Filled.Groups, strings.peakViewers, "$peakViewers", SignalCyan, Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AnalyticsKpiCard(Icons.Filled.VolunteerActivism, strings.totalDonations, "₹$totalDonations", SuperChatGold, Modifier.weight(1f))
                                AnalyticsKpiCard(Icons.Filled.Bolt, strings.avgLatency, "${avgLatency}ms", StudioEmerald, Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Trend Chart & Quality Breakdown
                item {
                    if (isWideScreen) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            PerformanceTrendChartCard(
                                title = strings.viewerTrend,
                                streams = streams,
                                modifier = Modifier.weight(1.3f)
                            )
                            QualityMixCard(
                                counts = resCounts,
                                total = totalStreams.coerceAtLeast(1),
                                modifier = Modifier.weight(0.9f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            PerformanceTrendChartCard(
                                title = strings.viewerTrend,
                                streams = streams,
                                modifier = Modifier.fillMaxWidth()
                            )
                            QualityMixCard(
                                counts = resCounts,
                                total = totalStreams.coerceAtLeast(1),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = strings.recentBroadcasts,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (streams.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                        ) {
                            Text(
                                text = "Complete your first broadcast in Studio to see live viewer curves, donations, and bitrate telemetry here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(streams, key = { it.id }) { stream ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stream.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${stream.resolution} • ${stream.sourceType.uppercase()} • ${stream.durationSeconds}s • ${stream.latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "₹${stream.totalDonationsInr}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SuperChatGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Peak ${stream.peakViewers}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsKpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PerformanceTrendChartCard(
    title: String,
    streams: List<LiveStream>,
    modifier: Modifier = Modifier
) {
    val recent = remember(streams) { streams.take(7).reversed() }
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
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
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Cyan: Viewers • Gold: Donations",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recent.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No stream sessions recorded yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val maxViewer = recent.maxOf { it.peakViewers }.coerceAtLeast(1L).toFloat()
                    val maxDonation = recent.maxOf { it.totalDonationsInr }.coerceAtLeast(100L).toFloat()

                    // Horizontal subtle grid lines
                    for (i in 0..3) {
                        val y = h * (i / 3f)
                        drawLine(
                            color = gridColor.copy(alpha = 0.45f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val slotWidth = w / recent.size.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.42f).coerceAtMost(34.dp.toPx())
                    val donationPath = Path()

                    recent.forEachIndexed { idx, stream ->
                        val centerX = slotWidth * idx + slotWidth / 2f
                        val barRatio = (stream.peakViewers.toFloat() / maxViewer).coerceIn(0.08f, 1f)
                        val barHeight = h * 0.85f * barRatio
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                listOf(SignalCyan, SignalCyan.copy(alpha = 0.25f))
                            ),
                            topLeft = Offset(centerX - barWidth / 2f, h - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )

                        val donationY = h - (h * 0.82f * (stream.totalDonationsInr.toFloat() / maxDonation).coerceIn(0.05f, 1f))
                        if (idx == 0) donationPath.moveTo(centerX, donationY)
                        else donationPath.lineTo(centerX, donationY)

                        drawCircle(
                            color = SuperChatGold,
                            radius = 4.5.dp.toPx(),
                            center = Offset(centerX, donationY)
                        )
                    }

                    if (recent.size > 1) {
                        drawPath(
                            path = donationPath,
                            color = SuperChatGold,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityMixCard(
    counts: Map<String, Int>,
    total: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Resolution Quality Mix", style = MaterialTheme.typography.titleMedium)
            listOf(
                Triple("1080p", counts["1080p"] ?: 0, LiveCrimson),
                Triple("720p", counts["720p"] ?: 0, SignalCyan),
                Triple("480p", counts["480p"] ?: 0, SuperChatGold)
            ).forEach { (label, count, color) ->
                val progress = (count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "$count streams",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
