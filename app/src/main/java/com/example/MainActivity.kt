package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.YyMindApp
import com.example.viewmodel.YyMindViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: YyMindViewModel by viewModels()
    private val launchRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            val isDark = when (settings.darkThemeOverride) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            // Keep status & navigation bar icons readable in Light and Dark mode
            LaunchedEffect(isDark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }

            // Dynamically apply/clear FLAG_SECURE screenshot protection (Section 11)
            LaunchedEffect(settings.flagSecureEnabled) {
                if (settings.flagSecureEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            YyMindApp(
                viewModel = viewModel,
                initialRoute = launchRouteState.value
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModel.handleTrimMemory(level)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // Quick Settings Tile launch route
        val tileRoute = intent.getStringExtra("yy_assistant_launch_route")
        if (!tileRoute.isNullOrBlank()) {
            launchRouteState.value = tileRoute
            return
        }

        // Android Share Target receiver (text, images, PDFs)
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type.orEmpty()
            when {
                type.startsWith("text/") -> {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        viewModel.handleIncomingShareText(sharedText)
                        launchRouteState.value = "chat"
                    }
                }
                type.startsWith("image/") -> {
                    val uri = extractStreamUri(intent)
                    if (uri != null) {
                        viewModel.attachImageToChat(uri)
                        launchRouteState.value = "chat"
                    }
                }
                type == "application/pdf" -> {
                    val uri = extractStreamUri(intent)
                    if (uri != null) {
                        viewModel.attachDocumentToChat(uri)
                        launchRouteState.value = "chat"
                    }
                }
            }
        }
    }

    private fun extractStreamUri(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
}
