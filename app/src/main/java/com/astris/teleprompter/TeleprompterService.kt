package com.astris.teleprompter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.astris.teleprompter.ui.theme.TeleprompterTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val PREFS_NAME = "teleprompter_settings"
private const val KEY_SPEED = "speed"
private const val KEY_ALPHA = "alpha"
private const val KEY_ROTATION = "rotation"

class TeleprompterService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: ComposeView
    private lateinit var customLifecycleOwner: CustomLifecycleOwner
    private lateinit var prefs: SharedPreferences

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        customLifecycleOwner = CustomLifecycleOwner()
        customLifecycleOwner.performRestore(null)
        customLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(customLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(customLifecycleOwner)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        customLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        val text = intent?.getStringExtra("text") ?: ""
        showFloatingWindow(text)
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "teleprompter_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Teleprompter Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Teleprompter Active")
            .setContentText("Tap to open the app.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    @SuppressLint("UnrememberedMutableState")
    private fun showFloatingWindow(text: String) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        floatingView.setContent {
            TeleprompterTheme {
                TeleprompterView(
                    text = text,
                    initialSpeed = prefs.getFloat(KEY_SPEED, 3f),
                    initialAlpha = prefs.getFloat(KEY_ALPHA, 0.8f),
                    initialRotation = prefs.getFloat(KEY_ROTATION, 0f),
                    onClose = { stopSelf() },
                    onDrag = { x, y ->
                        params.x += x.roundToInt()
                        params.y += y.roundToInt()
                        windowManager.updateViewLayout(floatingView, params)
                    },
                    onSettingsChange = { speed, alpha, rotation ->
                        prefs.edit()
                            .putFloat(KEY_SPEED, speed)
                            .putFloat(KEY_ALPHA, alpha)
                            .putFloat(KEY_ROTATION, rotation)
                            .apply()
                    }
                )
            }
        }
        windowManager.addView(floatingView, params)
    }


    override fun onDestroy() {
        super.onDestroy()
        customLifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        windowManager.removeView(floatingView)
    }
}

@Composable
fun TeleprompterView(
    text: String,
    initialSpeed: Float,
    initialAlpha: Float,
    initialRotation: Float,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onSettingsChange: (Float, Float, Float) -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(initialSpeed) }
    var rotation by remember { mutableStateOf(initialRotation) }
    var alpha by remember { mutableStateOf(initialAlpha) }
    var showTransparencySlider by remember { mutableStateOf(false) }
    var showSpeedSlider by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(isPlaying, speed) {
        if (isPlaying) {
            val scrollDelay = (100 / speed).toLong()
            while (true) {
                scrollState.scrollTo(scrollState.value + 1)
                delay(scrollDelay)
            }
        }
    }

    Card(
        modifier = Modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .graphicsLayer(rotationZ = rotation),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = alpha))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .height(200.dp)
                    .verticalScroll(scrollState),
                color = Color.White,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (showTransparencySlider) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = alpha,
                        onValueChange = { newAlpha ->
                            alpha = newAlpha
                            onSettingsChange(speed, alpha, rotation)
                        },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showTransparencySlider = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Slider", tint = Color.White)
                    }
                }
            }

            if (showSpeedSlider) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = speed,
                        onValueChange = { newSpeed ->
                            speed = newSpeed
                            onSettingsChange(speed, alpha, rotation)
                        },
                        valueRange = 1f..10f,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSpeedSlider = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close Slider", tint = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isPlaying = !isPlaying
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }

                IconButton(onClick = {
                    showTransparencySlider = !showTransparencySlider
                    if (showTransparencySlider) showSpeedSlider = false
                }) {
                    Icon(Icons.Default.Tonality, contentDescription = "Transparency", tint = Color.White)
                }

                IconButton(onClick = {
                    showSpeedSlider = !showSpeedSlider
                    if (showSpeedSlider) showTransparencySlider = false
                }) {
                    Icon(Icons.Default.Speed, contentDescription = "Speed", tint = Color.White)
                }

                IconButton(onClick = {
                    rotation = if (rotation == 0f) 90f else 0f
                    onSettingsChange(speed, alpha, rotation)
                }) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                }
            }
        }
    }
}
