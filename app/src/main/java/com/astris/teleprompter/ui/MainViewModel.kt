package com.astris.teleprompter.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astris.teleprompter.data.Script
import com.astris.teleprompter.data.ScriptRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private const val PREFS_NAME = "teleprompter_main_settings"
private const val KEY_IS_DARK_THEME = "is_dark_theme"

data class MainUiState(
    val scripts: List<Script> = emptyList()
)

class MainViewModel(
    application: Application,
    private val scriptRepository: ScriptRepository
) : ViewModel() {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_IS_DARK_THEME, true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    init {
        viewModelScope.launch {
            scriptRepository.getAllScripts().collect { scripts ->
                _uiState.update { it.copy(scripts = scripts) }
            }
        }

        viewModelScope.launch {
            searchQuery.collect { query ->
                val scripts = if (query.isEmpty()) {
                    scriptRepository.getAllScripts().first()
                } else {
                    scriptRepository.searchScripts(query).first()
                }
                _uiState.update { it.copy(scripts = scripts) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleTheme() {
        val newTheme = !_isDarkTheme.value
        _isDarkTheme.value = newTheme
        prefs.edit().putBoolean(KEY_IS_DARK_THEME, newTheme).apply()
    }
}
