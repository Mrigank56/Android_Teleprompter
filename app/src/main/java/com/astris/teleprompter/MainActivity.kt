package com.astris.teleprompter

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astris.teleprompter.data.Script
import com.astris.teleprompter.ui.MainViewModel
import com.astris.teleprompter.ui.ViewModelProvider
import com.astris.teleprompter.ui.theme.TeleprompterTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TeleprompterTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen(
        viewModel: MainViewModel = viewModel(factory = ViewModelProvider.Factory)
    ) {
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        var hasPermission by remember {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(this)
                } else {
                    true
                }
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                hasPermission = Settings.canDrawOverlays(this)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Search scripts") },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: Handle menu click */ }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* TODO: Handle search click */ }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    context.startActivity(Intent(context, ScriptEditorActivity::class.java))
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Script")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (!hasPermission) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                permissionLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Grant Overlay Permission")
                    }
                }
                if (uiState.scripts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No scripts yet. Tap the '+' button to add one.")
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        items(uiState.scripts) { script ->
                            ScriptItem(
                                script = script,
                                onClick = {
                                    val intent = Intent(context, ScriptEditorActivity::class.java)
                                    intent.putExtra(EXTRA_SCRIPT_ID, script.id)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ScriptItem(script: Script, onClick: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = script.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = script.content,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
