package com.astris.teleprompter

import android.app.Application
import com.astris.teleprompter.data.AppDatabase
import com.astris.teleprompter.data.ScriptRepository

class TeleprompterApplication : Application() {
    lateinit var scriptRepository: ScriptRepository

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        scriptRepository = ScriptRepository(database.scriptDao())
    }
}
