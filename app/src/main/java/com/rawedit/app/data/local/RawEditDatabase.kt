package com.rawedit.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id")
    fun observeById(id: Long): Flow<VideoEntity?>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun getById(id: Long): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity): Long

    @Update
    suspend fun update(video: VideoEntity)

    @Query("UPDATE videos SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE videos SET savedToGalleryUri = :galleryUri WHERE id = :id")
    suspend fun updateGalleryUri(id: Long, galleryUri: String)

    @Delete
    suspend fun delete(video: VideoEntity)
}

@Dao
interface LocalAssetDao {
    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LocalAssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: Long): LocalAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: LocalAssetEntity): Long

    @Query("UPDATE assets SET description = :description, safeZone = :safeZone WHERE id = :id")
    suspend fun updateDescription(id: Long, description: String, safeZone: String)

    @Delete
    suspend fun delete(asset: LocalAssetEntity)
}

@Database(
    entities = [ProjectEntity::class, VideoEntity::class, LocalAssetEntity::class],
    version = 2,
    exportSchema = false
)
abstract class RawEditDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun videoDao(): VideoDao
    abstract fun localAssetDao(): LocalAssetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN attachedAssetIds TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS assets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fileName TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        width INTEGER NOT NULL,
                        height INTEGER NOT NULL,
                        durationSec REAL NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        thumbPath TEXT NOT NULL,
                        description TEXT NOT NULL,
                        safeZone TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: RawEditDatabase? = null

        fun getInstance(context: Context): RawEditDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RawEditDatabase::class.java,
                    "raw_edit.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
