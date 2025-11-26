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
    @Query("SELECT * FROM scripts ORDER BY title ASC")
    fun getAllScripts(): Flow<List<Script>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    fun getScriptById(id: Int): Flow<Script>

    @Query("SELECT * FROM scripts WHERE title LIKE :query OR content LIKE :query")
    fun searchScripts(query: String): Flow<List<Script>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: Script): Long

    @Update
    suspend fun update(script: Script)

    @Delete
    suspend fun delete(script: Script)
}
