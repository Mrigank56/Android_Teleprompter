package com.astris.teleprompter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astris.teleprompter.data.Script
import com.astris.teleprompter.data.ScriptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel(private val scriptRepository: ScriptRepository) : ViewModel() {

    val uiState: StateFlow<MainUiState> =
        scriptRepository.getAllScripts().map { MainUiState(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MainUiState()
            )
}

data class MainUiState(
    val scripts: List<Script> = listOf()
)
