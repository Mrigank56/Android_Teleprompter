package com.astris.teleprompter.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astris.teleprompter.EXTRA_SCRIPT_ID
import com.astris.teleprompter.data.Script
import com.astris.teleprompter.data.ScriptRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScriptEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val scriptRepository: ScriptRepository
) : ViewModel() {

    private val scriptId: Int? = savedStateHandle[EXTRA_SCRIPT_ID]

    private val _uiState = MutableStateFlow(ScriptEditorUiState())
    val uiState: StateFlow<ScriptEditorUiState> = _uiState.asStateFlow()

    init {
        if (scriptId != null) {
            viewModelScope.launch {
                scriptRepository.getScriptById(scriptId).collect { script ->
                    _uiState.value = ScriptEditorUiState(
                        id = script.id,
                        title = script.title,
                        content = script.content,
                        isNewScript = false
                    )
                }
            }
        }
    }

    suspend fun saveScript() {
        val currentState = _uiState.value
        if (currentState.title.isNotBlank() && currentState.content.isNotBlank()) {
            val script = Script(
                id = currentState.id,
                title = currentState.title,
                content = currentState.content
            )
            if (currentState.isNewScript) {
                scriptRepository.insert(script)
            } else {
                scriptRepository.update(script)
            }
        }
    }

    suspend fun deleteScript() {
        val currentState = _uiState.value
        if (!currentState.isNewScript) {
            scriptRepository.delete(
                Script(
                    id = currentState.id,
                    title = currentState.title,
                    content = currentState.content
                )
            )
        }
    }

    fun updateTitle(newTitle: String) {
        _uiState.value = _uiState.value.copy(title = newTitle)
    }

    fun updateContent(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent)
    }
}

data class ScriptEditorUiState(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val isNewScript: Boolean = true
)
