package com.example.workouttracker.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    val timestampIso: String,
    val exercise: String,
    val reps: Int,
    val durationSeconds: Float,
    val totalXp: Float,
    val syncedToNotion: Boolean = false
)

@Entity(tableName = "daily_progress")
data class DailyProgressEntity(
    @PrimaryKey val date: String, // YYYY-MM-DD
    val pushups: Int = 0,
    val squats: Int = 0,
    val plankSeconds: Int = 0, // legacy column retained (ignored in logic)
    val bicepLeft: Int = 0,
    val bicepRight: Int = 0,
    val goalsMet: Boolean = false,
    val lastUpdatedIso: String
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val totalXp: Float = 0f,
    val name: String = "",
    val useCustomGoals: Boolean = false,
    val customPushGoal: Int = 10,
    val customSquatGoal: Int = 10,
    val customBicepGoal: Int = 60,
    val notionApiKey: String = "",
    val notionDbId: String = ""
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY id DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE syncedToNotion = 0")
    suspend fun getUnsyncedSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET syncedToNotion = 1 WHERE id IN (:ids)")
    suspend fun markSessionsSynced(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getCount(): Int

    @Query("SELECT SUM(reps) FROM sessions WHERE exercise = :exercise")
    suspend fun getTotalRepsForExercise(exercise: String): Int?

    @Query("SELECT SUM(totalXp) FROM sessions")
    suspend fun getTotalXp(): Float?

    @Query("SELECT MAX(reps) FROM sessions WHERE exercise = :exercise")
    suspend fun getBestSetForExercise(exercise: String): Int?

    @Query("SELECT SUM(totalXp) FROM sessions WHERE timestampIso >= :startDate AND timestampIso <= :endDate")
    suspend fun getXpSumBetween(startDate: String, endDate: String): Float?

    // Daily progress
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(progress: DailyProgressEntity)

    @Query("SELECT * FROM daily_progress WHERE date = :date")
    suspend fun getDaily(date: String): DailyProgressEntity?

    @Query("SELECT * FROM daily_progress ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentDaily(limit: Int): List<DailyProgressEntity>

    @Query("DELETE FROM daily_progress")
    suspend fun deleteAllDailyProgress()

    // User profile
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfile(): UserProfileEntity?
}

@Database(
    entities = [SessionEntity::class, DailyProgressEntity::class, UserProfileEntity::class],
    version = 7,
    exportSchema = false
)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: SessionDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS daily_progress (date TEXT NOT NULL PRIMARY KEY, pushups INTEGER NOT NULL DEFAULT 0, squats INTEGER NOT NULL DEFAULT 0, plankSeconds INTEGER NOT NULL DEFAULT 0, goalsMet INTEGER NOT NULL DEFAULT 0, lastUpdatedIso TEXT NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS user_profile (id INTEGER NOT NULL PRIMARY KEY, totalXp INTEGER NOT NULL DEFAULT 0)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3,4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add name column if missing
                db.execSQL("ALTER TABLE user_profile ADD COLUMN name TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_progress ADD COLUMN bicepLeft INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_progress ADD COLUMN bicepRight INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5,6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Migrate sessions totalXp to REAL
                db.execSQL("CREATE TABLE sessions_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, timestampIso TEXT NOT NULL, exercise TEXT NOT NULL, reps INTEGER NOT NULL, durationSeconds REAL NOT NULL, totalXp REAL NOT NULL)")
                db.execSQL("INSERT INTO sessions_new (id, timestampIso, exercise, reps, durationSeconds, totalXp) SELECT id, timestampIso, exercise, reps, durationSeconds, CAST(totalXp AS REAL) FROM sessions")
                db.execSQL("DROP TABLE sessions")
                db.execSQL("ALTER TABLE sessions_new RENAME TO sessions")

                // Migrate user_profile totalXp to REAL and add custom goal columns
                db.execSQL("CREATE TABLE user_profile_new (id INTEGER PRIMARY KEY NOT NULL, totalXp REAL NOT NULL DEFAULT 0.0, name TEXT NOT NULL DEFAULT '', useCustomGoals INTEGER NOT NULL DEFAULT 0, customPushGoal INTEGER NOT NULL DEFAULT 10, customSquatGoal INTEGER NOT NULL DEFAULT 10, customBicepGoal INTEGER NOT NULL DEFAULT 60)")
                db.execSQL("INSERT INTO user_profile_new (id, totalXp, name) SELECT id, CAST(totalXp AS REAL), name FROM user_profile")
                db.execSQL("DROP TABLE user_profile")
                db.execSQL("ALTER TABLE user_profile_new RENAME TO user_profile")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6,7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN syncedToNotion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN notionApiKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_profile ADD COLUMN notionDbId TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): SessionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SessionDatabase::class.java,
                    "session_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
