package com.astris.teleprompter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.astris.teleprompter.ui.theme.TeleprompterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PREFS_NAME = "teleprompter_settings"
private const val KEY_SPEED = "speed"
private const val KEY_ALPHA = "alpha"
private const val KEY_MIRROR = "mirror"
private const val KEY_FONT_SIZE = "font_size"
private const val ACTION_STOP = "com.astris.teleprompter.action.STOP"

private const val MIN_SPEED_PX = 15f
private const val MAX_SPEED_PX = 300f
private const val DEFAULT_SPEED_PX = 60f

private const val MIN_BG_ALPHA = 0f
private const val MAX_BG_ALPHA = 0.7f
private const val DEFAULT_BG_ALPHA = 0.18f

private const val MIN_FONT_SP = 16f
private const val MAX_FONT_SP = 40f
private const val DEFAULT_FONT_SP = 24f

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
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
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

        val stopIntent = Intent(this, TeleprompterService::class.java).setAction(ACTION_STOP)
        val stopPendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, stopPendingIntentFlags)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Teleprompter Active")
            .setContentText("Tap to open the app.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    @SuppressLint("UnrememberedMutableState")
    private fun showFloatingWindow(text: String) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        // Pre-1.0 builds stored speed on a 1..10 scale and defaulted alpha to 0.8 (near-opaque).
        // Treat values still at those legacy defaults as unset so upgrading users land on the
        // new smooth-scroll / camera-transparent defaults instead of an unreadable old number.
        val storedSpeed = prefs.getFloat(KEY_SPEED, DEFAULT_SPEED_PX)
        val migratedSpeed = if (storedSpeed < MIN_SPEED_PX) DEFAULT_SPEED_PX else storedSpeed
        val storedAlpha = prefs.getFloat(KEY_ALPHA, DEFAULT_BG_ALPHA)
        val migratedAlpha = if (storedAlpha >= 0.75f) DEFAULT_BG_ALPHA else storedAlpha

        floatingView.setContent {
            TeleprompterTheme {
                TeleprompterView(
                    text = text,
                    initialSpeed = migratedSpeed,
                    initialAlpha = migratedAlpha,
                    initialMirror = prefs.getBoolean(KEY_MIRROR, false),
                    initialFontSize = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SP),
                    onClose = { stopSelf() },
                    onDrag = { x, y ->
                        params.x += x.roundToInt()
                        params.y += y.roundToInt()
                        windowManager.updateViewLayout(floatingView, params)
                    },
                    onSettingsChange = { speed, alpha, fontSize, mirror ->
                        prefs.edit()
                            .putFloat(KEY_SPEED, speed)
                            .putFloat(KEY_ALPHA, alpha)
                            .putFloat(KEY_FONT_SIZE, fontSize)
                            .putBoolean(KEY_MIRROR, mirror)
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
    initialMirror: Boolean,
    initialFontSize: Float,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onSettingsChange: (speed: Float, alpha: Float, fontSize: Float, mirror: Boolean) -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(initialSpeed) }
    var bgAlpha by remember { mutableStateOf(initialAlpha) }
    var mirrored by remember { mutableStateOf(initialMirror) }
    var fontSize by remember { mutableStateOf(initialFontSize) }
    var countdownValue by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableStateOf(0) }
    var textAreaHeight by remember { mutableStateOf(240.dp) }
    var contentHeightPx by remember { mutableStateOf(0) }

    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    fun poke() {
        interactionTick++
        controlsVisible = true
    }

    fun pushSettings() {
        onSettingsChange(speed, bgAlpha, fontSize, mirrored)
    }

    val wordCount = remember(text) { text.split(Regex("\\s+")).count { it.isNotBlank() } }
    val estimatedWpm = remember(speed, contentHeightPx, wordCount) {
        val totalSeconds = if (speed > 0f) contentHeightPx / speed else 0f
        if (totalSeconds > 0f) (wordCount / (totalSeconds / 60f)).roundToInt() else 0
    }
    val progress = if (scrollState.maxValue > 0) {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    } else 0f

    // Auto-hide controls 3s after the last interaction while scrolling, so the camera view
    // underneath stays unobstructed; any tap/drag resets the timer via poke().
    LaunchedEffect(isPlaying, interactionTick) {
        if (isPlaying) {
            delay(3000)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }

    // Frame-driven scroll with sub-pixel accumulation: smooth and frame-rate independent at
    // any speed, unlike a fixed-delay/1px-per-tick loop which stutters at low speeds.
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameTimeNanos = -1L
        var accumulator = 0f
        while (isActive) {
            val frameTimeNanos = withFrameNanos { it }
            if (lastFrameTimeNanos >= 0L) {
                val dtSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                accumulator += speed * dtSeconds
                val deltaPixels = accumulator.toInt()
                if (deltaPixels > 0) {
                    accumulator -= deltaPixels
                    scrollState.dispatchRawDelta(deltaPixels.toFloat())
                    if (scrollState.value >= scrollState.maxValue) {
                        isPlaying = false
                        finished = true
                    }
                }
            }
            lastFrameTimeNanos = frameTimeNanos
        }
    }

    val onPlayToggle: () -> Unit = {
        poke()
        when {
            isPlaying -> isPlaying = false
            countdownValue > 0 -> countdownValue = 0
            else -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    finished = false
                    countdownValue = 3
                    while (countdownValue > 0) {
                        delay(600)
                        countdownValue -= 1
                    }
                    isPlaying = true
                }
            }
        }
    }

    Card(
        modifier = Modifier.padding(8.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = bgAlpha))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                poke()
                                onDrag(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DragIndicator,
                        contentDescription = "Drag to move",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color.White.copy(alpha = 0.85f),
                trackColor = Color.White.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(textAreaHeight)
            ) {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState, enabled = !isPlaying)
                        .pointerInput(Unit) {
                            detectTapGestures { poke() }
                        }
                        .graphicsLayer(scaleX = if (mirrored) -1f else 1f),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5f).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.9f), blurRadius = 10f, offset = Offset(0f, 0f))
                    ),
                    onTextLayout = { layoutResult -> contentHeightPx = layoutResult.size.height }
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = bgAlpha.coerceAtLeast(0.35f)), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(24.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = bgAlpha.coerceAtLeast(0.35f)))
                            )
                        )
                )

                if (countdownValue > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countdownValue.toString(),
                            color = Color.White,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                poke()
                                val deltaDp = with(density) { dragAmount.y.toDp() }
                                textAreaHeight = (textAreaHeight + deltaDp).coerceIn(140.dp, 520.dp)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.UnfoldMore,
                        contentDescription = "Resize",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (finished && !isPlaying) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("End of script", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }

            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            poke()
                            isPlaying = false
                            countdownValue = 0
                            finished = false
                            scope.launch { scrollState.scrollTo(0) }
                        }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Restart", tint = Color.White)
                        }

                        IconButton(onClick = onPlayToggle) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(44.dp),
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            poke()
                            mirrored = !mirrored
                            pushSettings()
                        }) {
                            Icon(
                                Icons.Default.Flip,
                                contentDescription = "Mirror",
                                tint = if (mirrored) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        IconButton(onClick = {
                            poke()
                            showSettings = !showSettings
                        }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Settings",
                                tint = if (showSettings) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        IconButton(onClick = {
                            poke()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClose()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    if (showSettings) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(
                                "Background  ${(bgAlpha * 100).roundToInt()}%",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Slider(
                                value = bgAlpha,
                                onValueChange = { poke(); bgAlpha = it; pushSettings() },
                                valueRange = MIN_BG_ALPHA..MAX_BG_ALPHA
                            )

                            Text(
                                if (estimatedWpm > 0) "Speed  ~$estimatedWpm wpm" else "Speed",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    poke()
                                    speed = (speed - 10f).coerceIn(MIN_SPEED_PX, MAX_SPEED_PX)
                                    pushSettings()
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Remove, contentDescription = "Slower", tint = Color.White)
                                }
                                Slider(
                                    value = speed,
                                    onValueChange = { poke(); speed = it; pushSettings() },
                                    valueRange = MIN_SPEED_PX..MAX_SPEED_PX,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    poke()
                                    speed = (speed + 10f).coerceIn(MIN_SPEED_PX, MAX_SPEED_PX)
                                    pushSettings()
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = "Faster", tint = Color.White)
                                }
                            }

                            Text(
                                "Text size  ${fontSize.roundToInt()}sp",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Slider(
                                value = fontSize,
                                onValueChange = { poke(); fontSize = it; pushSettings() },
                                valueRange = MIN_FONT_SP..MAX_FONT_SP
                            )
                        }
                    }
                }
            }
        }
    }
}
