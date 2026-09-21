package com.astris.teleprompter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.astris.teleprompter.ui.ScriptEditorViewModel
import com.astris.teleprompter.ui.ViewModelFactory
import com.astris.teleprompter.ui.theme.TeleprompterTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val EXTRA_SCRIPT_ID = "com.astris.teleprompter.SCRIPT_ID"
private const val REFERENCE_WPM = 140

class ScriptEditorActivity : ComponentActivity() {

    private lateinit var viewModel: ScriptEditorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val factory = ViewModelFactory(this, application, intent.extras)
        viewModel = ViewModelProvider(this, factory)[ScriptEditorViewModel::class.java]

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            TeleprompterTheme(darkTheme = isDarkTheme) {
                ScriptEditorScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScriptEditorScreen(
    viewModel: ScriptEditorViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isKeyboardOpen = WindowInsets.isImeVisible
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val wordCount = remember(uiState.content) {
        val trimmed = uiState.content.trim()
        if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).size
    }
    val estimatedMinutes = if (wordCount > 0) (wordCount / REFERENCE_WPM.toFloat()) else 0f

    val playAction: () -> Unit = {
        when {
            uiState.content.isBlank() -> {
                Toast.makeText(context, "Add some script content first", Toast.LENGTH_SHORT).show()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context) -> {
                Toast.makeText(
                    context,
                    "Enable \"Display over other apps\" permission first",
                    Toast.LENGTH_LONG
                ).show()
            }
            else -> {
                coroutineScope.launch {
                    // Save the script first
                    viewModel.saveScript()

                    // Then start the teleprompter service
                    val serviceIntent = Intent(context, TeleprompterService::class.java)
                    serviceIntent.putExtra("text", uiState.content)
                    context.startService(serviceIntent)

                    // Finally, open the camera app in video mode (this is a teleprompter, not a photo tool)
                    val cameraIntent = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
                    context.startActivity(cameraIntent)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!isKeyboardOpen) {
                FloatingActionButton(
                    onClick = playAction,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Teleprompter")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Header: back, title, actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
                        letterSpacing = MaterialTheme.typography.titleLarge.letterSpacing,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (uiState.title.isEmpty()) {
                                Text(
                                    "Title",
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (isKeyboardOpen) {
                    IconButton(onClick = playAction) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Start Teleprompter",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = {
                    coroutineScope.launch {
                        viewModel.saveScript()
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        (context as? ComponentActivity)?.finish()
                    }
                }) {
                    Icon(
                        Icons.Default.Done,
                        contentDescription = "Save Script",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!uiState.isNewScript) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Script",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Word count / estimated read time chip
            Box(
                modifier = Modifier
                    .padding(start = 20.dp, top = 4.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (wordCount > 0) {
                        val minutesLabel = if (estimatedMinutes < 1f) "<1 min" else "~${estimatedMinutes.roundToInt()} min"
                        "$wordCount words · $minutesLabel at $REFERENCE_WPM wpm"
                    } else {
                        "Start typing your script"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Script "page"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
            ) {
                BasicTextField(
                    value = uiState.content,
                    onValueChange = { viewModel.updateContent(it) },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (uiState.content.isEmpty()) {
                                Text(
                                    "Script content",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete script?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    coroutineScope.launch {
                        viewModel.deleteScript()
                        (context as? ComponentActivity)?.finish()
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
