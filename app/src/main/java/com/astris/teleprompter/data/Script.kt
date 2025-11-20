package com.astris.teleprompter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scripts")
data class Script(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val content: String
)
