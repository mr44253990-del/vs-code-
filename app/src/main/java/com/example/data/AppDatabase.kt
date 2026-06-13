package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY id DESC")
    fun getAllProjects(): Flow<List<Project>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Query("SELECT * FROM files WHERE projectId = :projectId")
    fun getFilesForProject(projectId: Long): Flow<List<ProjectFile>>

    @Query("SELECT * FROM files WHERE projectId = :projectId")
    suspend fun getFilesForProjectSync(projectId: Long): List<ProjectFile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ProjectFile): Long

    @Update
    suspend fun updateFile(file: ProjectFile)

    @Delete
    suspend fun deleteFile(file: ProjectFile)

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: Long): ProjectFile?

    @Query("DELETE FROM files WHERE projectId = :projectId")
    suspend fun deleteProjectFiles(projectId: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions")
    fun getAllExtensions(): Flow<List<ExtensionItem>>

    @Query("SELECT * FROM extensions")
    suspend fun getAllExtensionsSync(): List<ExtensionItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtensions(items: List<ExtensionItem>)

    @Query("UPDATE extensions SET isInstalled = :isInstalled WHERE id = :id")
    suspend fun updateInstalledStatus(id: String, isInstalled: Boolean)
}

@Database(
    entities = [Project::class, ProjectFile::class, ChatMessage::class, ExtensionItem::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatDao(): ChatDao
    abstract fun extensionDao(): ExtensionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rakib_code_studio_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
