package com.example.workouttracker.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SessionDatabase_Impl extends SessionDatabase {
  private volatile SessionDao _sessionDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(5) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestampIso` TEXT NOT NULL, `exercise` TEXT NOT NULL, `reps` INTEGER NOT NULL, `durationSeconds` REAL NOT NULL, `totalXp` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_progress` (`date` TEXT NOT NULL, `pushups` INTEGER NOT NULL, `squats` INTEGER NOT NULL, `plankSeconds` INTEGER NOT NULL, `bicepLeft` INTEGER NOT NULL, `bicepRight` INTEGER NOT NULL, `goalsMet` INTEGER NOT NULL, `lastUpdatedIso` TEXT NOT NULL, PRIMARY KEY(`date`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `totalXp` INTEGER NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7b3d949cdf81e293e3d5ed99e1acd3e6')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sessions`");
        db.execSQL("DROP TABLE IF EXISTS `daily_progress`");
        db.execSQL("DROP TABLE IF EXISTS `user_profile`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSessions = new HashMap<String, TableInfo.Column>(6);
        _columnsSessions.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("timestampIso", new TableInfo.Column("timestampIso", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("exercise", new TableInfo.Column("exercise", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("reps", new TableInfo.Column("reps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("durationSeconds", new TableInfo.Column("durationSeconds", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSessions.put("totalXp", new TableInfo.Column("totalXp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSessions = new TableInfo("sessions", _columnsSessions, _foreignKeysSessions, _indicesSessions);
        final TableInfo _existingSessions = TableInfo.read(db, "sessions");
        if (!_infoSessions.equals(_existingSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "sessions(com.example.workouttracker.db.SessionEntity).\n"
                  + " Expected:\n" + _infoSessions + "\n"
                  + " Found:\n" + _existingSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsDailyProgress = new HashMap<String, TableInfo.Column>(8);
        _columnsDailyProgress.put("date", new TableInfo.Column("date", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyProgress.put("pushups", new TableInfo.Column("pushups", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyProgress.put("squats", new TableInfo.Column("squats", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyProgress.put("plankSeconds", new TableInfo.Column("plankSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyProgress.put("bicepLeft", new TableInfo.Column("bicepLeft", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyProgress.put("bicepRight", new TableInfo.Column("bicepRight", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyProgress.put("goalsMet", new TableInfo.Column("goalsMet", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyProgress.put("lastUpdatedIso", new TableInfo.Column("lastUpdatedIso", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDailyProgress = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDailyProgress = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDailyProgress = new TableInfo("daily_progress", _columnsDailyProgress, _foreignKeysDailyProgress, _indicesDailyProgress);
        final TableInfo _existingDailyProgress = TableInfo.read(db, "daily_progress");
        if (!_infoDailyProgress.equals(_existingDailyProgress)) {
          return new RoomOpenHelper.ValidationResult(false, "daily_progress(com.example.workouttracker.db.DailyProgressEntity).\n"
                  + " Expected:\n" + _infoDailyProgress + "\n"
                  + " Found:\n" + _existingDailyProgress);
        }
        final HashMap<String, TableInfo.Column> _columnsUserProfile = new HashMap<String, TableInfo.Column>(3);
        _columnsUserProfile.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("totalXp", new TableInfo.Column("totalXp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsUserProfile.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysUserProfile = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesUserProfile = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoUserProfile = new TableInfo("user_profile", _columnsUserProfile, _foreignKeysUserProfile, _indicesUserProfile);
        final TableInfo _existingUserProfile = TableInfo.read(db, "user_profile");
        if (!_infoUserProfile.equals(_existingUserProfile)) {
          return new RoomOpenHelper.ValidationResult(false, "user_profile(com.example.workouttracker.db.UserProfileEntity).\n"
                  + " Expected:\n" + _infoUserProfile + "\n"
                  + " Found:\n" + _existingUserProfile);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "7b3d949cdf81e293e3d5ed99e1acd3e6", "c66bf51720d74f3fc820e2e611927d65");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "sessions","daily_progress","user_profile");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `sessions`");
      _db.execSQL("DELETE FROM `daily_progress`");
      _db.execSQL("DELETE FROM `user_profile`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SessionDao.class, SessionDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SessionDao sessionDao() {
    if (_sessionDao != null) {
      return _sessionDao;
    } else {
      synchronized(this) {
        if(_sessionDao == null) {
          _sessionDao = new SessionDao_Impl(this);
        }
        return _sessionDao;
      }
    }
  }
}
