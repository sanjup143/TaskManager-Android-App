package com.sanju.taskmanager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String,
    val priority: String,
    val category: String,
    val dueDate: String,
    val isCompleted: Boolean = false
)