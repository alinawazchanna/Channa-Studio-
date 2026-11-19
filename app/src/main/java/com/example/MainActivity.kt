package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.service.StreamNotificationHelper
import com.example.ui.StreamPulseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StreamNotificationHelper.ensureChannels(this)
        setContent {
            StreamPulseApp()
        }
    }
}
