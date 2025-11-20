package com.astris.teleprompter.data

import kotlinx.coroutines.flow.Flow

class ScriptRepository(private val scriptDao: ScriptDao) {

    fun getAllScripts(): Flow<List<Script>> = scriptDao.getAllScripts()

    fun getScriptById(id: Int): Flow<Script> = scriptDao.getScriptById(id)

    suspend fun insert(script: Script) {
        scriptDao.insert(script)
    }

    suspend fun update(script: Script) {
        scriptDao.update(script)
    }

    suspend fun delete(script: Script) {
        scriptDao.delete(script)
    }
}
