package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "files")
data class ProjectFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val path: String, // e.g. "css/style.css" or "index.html"
    val name: String,
    val content: String,
    val language: String, // e.g. "html", "css", "javascript", "markdown"
    val isFolder: Boolean,
    val parentPath: String // e.g. "css", ""
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "user" or "ai"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "extensions")
data class ExtensionItem(
    @PrimaryKey val id: String, // e.g. "live-server"
    val name: String,
    val description: String,
    val author: String,
    val isInstalled: Boolean,
    val downloads: String,
    val rating: Double
)
