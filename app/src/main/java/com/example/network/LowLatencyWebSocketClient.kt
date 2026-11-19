package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class StreamTransportTelemetry(
    val wsState: WsConnectionState = WsConnectionState.DISCONNECTED,
    val latencyMs: Long = 0L,
    val packetsSent: Long = 0L,
    val lastMessagePreview: String = "",
    val rtmpReachable: Boolean? = null,
    val rtmpStatusNote: String = ""
)

class LowLatencyWebSocketClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var lastPingSentAtMs: Long = 0L

    private val _telemetry = MutableStateFlow(StreamTransportTelemetry())
    val telemetry: StateFlow<StreamTransportTelemetry> = _telemetry.asStateFlow()

    fun connect(
        url: String,
        streamId: String,
        resolutionCode: String,
        scope: CoroutineScope
    ) {
        disconnect()
        val safeUrl = url.trim().ifBlank { "wss://ws.postman-echo.com/raw" }
        if (!safeUrl.startsWith("ws://") && !safeUrl.startsWith("wss://")) {
            _telemetry.value = _telemetry.value.copy(
                wsState = WsConnectionState.ERROR,
                lastMessagePreview = "Invalid WebSocket URL (must start with ws:// or wss://)"
            )
            return
        }

        _telemetry.value = _telemetry.value.copy(
            wsState = WsConnectionState.CONNECTING,
            lastMessagePreview = "Connecting to $safeUrl..."
        )

        val request = Request.Builder().url(safeUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _telemetry.value = _telemetry.value.copy(
                    wsState = WsConnectionState.CONNECTED,
                    lastMessagePreview = "Low-latency relay open"
                )
                sendControlPacket(
                    type = "stream_init",
                    streamId = streamId,
                    payload = mapOf("resolution" to resolutionCode, "protocol" to "ws-ll")
                )
                startHeartbeat(scope, streamId, resolutionCode)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val now = System.currentTimeMillis()
                val rtt = if (lastPingSentAtMs > 0L) (now - lastPingSentAtMs).coerceIn(12L, 9999L) else _telemetry.value.latencyMs
                _telemetry.value = _telemetry.value.copy(
                    wsState = WsConnectionState.CONNECTED,
                    latencyMs = rtt,
                    lastMessagePreview = text.take(80)
                )
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w("LowLatencyWS", "WebSocket transport error: ${t.message}")
                heartbeatJob?.cancel()
                _telemetry.value = _telemetry.value.copy(
                    wsState = WsConnectionState.ERROR,
                    lastMessagePreview = t.localizedMessage ?: "Relay unreachable"
                )
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                _telemetry.value = _telemetry.value.copy(
                    wsState = WsConnectionState.DISCONNECTED,
                    lastMessagePreview = "Relay closed ($code)"
                )
            }
        })
    }

    private fun startHeartbeat(scope: CoroutineScope, streamId: String, resolutionCode: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                lastPingSentAtMs = System.currentTimeMillis()
                sendControlPacket(
                    type = "ping_telemetry",
                    streamId = streamId,
                    payload = mapOf(
                        "ts" to lastPingSentAtMs,
                        "resolution" to resolutionCode
                    )
                )
                delay(4000L)
            }
        }
    }

    fun sendControlPacket(type: String, streamId: String, payload: Map<String, Any>) {
        val json = JSONObject().apply {
            put("type", type)
            put("streamId", streamId)
            payload.forEach { (k, v) -> put(k, v) }
        }.toString()
        val sent = webSocket?.send(json) == true
        if (sent) {
            _telemetry.value = _telemetry.value.copy(
                packetsSent = _telemetry.value.packetsSent + 1L
            )
        }
    }

    suspend fun verifyRtmpEndpoint(rtmpUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val trimmed = rtmpUrl.trim()
        if (!trimmed.startsWith("rtmp://") && !trimmed.startsWith("rtmps://")) {
            val result = false to "RTMP URL must start with rtmp:// or rtmps://"
            _telemetry.value = _telemetry.value.copy(rtmpReachable = false, rtmpStatusNote = result.second)
            return@withContext result
        }
        try {
            val uri = URI(trimmed)
            val host = uri.host ?: throw IllegalArgumentException("Missing RTMP host")
            val port = if (uri.port > 0) uri.port else if (trimmed.startsWith("rtmps://")) 443 else 1935
            Socket().use { socket ->
                val start = System.currentTimeMillis()
                socket.connect(InetSocketAddress(host, port), 3500)
                val rtt = System.currentTimeMillis() - start
                val note = "Ingest $host:$port reachable (${rtt}ms)"
                _telemetry.value = _telemetry.value.copy(rtmpReachable = true, rtmpStatusNote = note)
                true to note
            }
        } catch (e: Exception) {
            val note = "RTMP endpoint check: ${e.localizedMessage ?: "unreachable"}"
            _telemetry.value = _telemetry.value.copy(rtmpReachable = false, rtmpStatusNote = note)
            false to note
        }
    }

    fun disconnect() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "Stream ended")
        webSocket = null
        _telemetry.value = _telemetry.value.copy(
            wsState = WsConnectionState.DISCONNECTED
        )
    }
}
