package com.astris.teleprompter.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.astris.teleprompter.TeleprompterApplication

object ViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            MainViewModel(
                teleprompterApplication().scriptRepository
            )
        }
        initializer {
            ScriptEditorViewModel(
                this.createSavedStateHandle(),
                teleprompterApplication().scriptRepository
            )
        }
    }
}

fun CreationExtras.teleprompterApplication(): TeleprompterApplication {
    return (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TeleprompterApplication)
}
