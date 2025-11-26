package com.astris.teleprompter.ui

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.astris.teleprompter.data.AppDatabase
import com.astris.teleprompter.data.ScriptRepository

class ViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val application: Application,
    private val defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        val database = AppDatabase.getDatabase(application)
        val scriptRepository = ScriptRepository(database.scriptDao())

        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(application, scriptRepository) as T
            }
            modelClass.isAssignableFrom(ScriptEditorViewModel::class.java) -> {
                ScriptEditorViewModel(application, handle, scriptRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
