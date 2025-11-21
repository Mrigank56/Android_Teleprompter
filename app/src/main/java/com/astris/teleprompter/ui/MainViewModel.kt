package com.astris.teleprompter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astris.teleprompter.data.Script
import com.astris.teleprompter.data.ScriptRepository
import kotlinx.coroutines.flow.*

class MainViewModel(private val scriptRepository: ScriptRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<MainUiState> =
        scriptRepository.getAllScripts()
            .combine(_searchQuery) { scripts, query ->
                if (query.isBlank()) {
                    MainUiState(scripts)
                } else {
                    val filteredScripts = scripts.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.content.contains(query, ignoreCase = true)
                    }
                    MainUiState(filteredScripts)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = MainUiState()
            )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}

data class MainUiState(
    val scripts: List<Script> = listOf()
)
