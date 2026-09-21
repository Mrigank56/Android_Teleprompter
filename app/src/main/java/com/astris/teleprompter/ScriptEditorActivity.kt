package com.astris.teleprompter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        topBar = {
            TopAppBar(
                title = {
                    BasicTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (uiState.title.isEmpty()) {
                                    Text("Title", fontSize = 20.sp, color = Color.Gray)
                                }
                                innerTextField()
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    if (isKeyboardOpen) {
                        IconButton(onClick = playAction) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Teleprompter")
                        }
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.saveScript()
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            (context as? ComponentActivity)?.finish()
                        }
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "Save Script")
                    }
                    if (!uiState.isNewScript) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Script")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isKeyboardOpen) {
                FloatingActionButton(onClick = playAction) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Teleprompter")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .imePadding()
        ) {
            Text(
                text = if (wordCount > 0) {
                    val minutesLabel = if (estimatedMinutes < 1f) "<1 min" else "~${estimatedMinutes.roundToInt()} min"
                    "$wordCount words · $minutesLabel at $REFERENCE_WPM wpm"
                } else {
                    "Start typing your script"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            BasicTextField(
                value = uiState.content,
                onValueChange = { viewModel.updateContent(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.content.isEmpty()) {
                            Text("Script content", fontSize = 16.sp, color = Color.Gray)
                        }
                        innerTextField()
                    }
                }
            )
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
                    Text("Delete")
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
