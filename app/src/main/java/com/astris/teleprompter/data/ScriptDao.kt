package com.astris.teleprompter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {

    @Query("SELECT * FROM scripts ORDER BY id DESC")
    fun getAllScripts(): Flow<List<Script>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    fun getScriptById(id: Int): Flow<Script>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: Script)

    @Update
    suspend fun update(script: Script)

    @Delete
    suspend fun delete(script: Script)
}
