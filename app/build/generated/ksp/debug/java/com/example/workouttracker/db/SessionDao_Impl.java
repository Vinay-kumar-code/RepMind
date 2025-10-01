package com.example.workouttracker.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SessionDao_Impl implements SessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SessionEntity> __insertionAdapterOfSessionEntity;

  private final EntityInsertionAdapter<DailyProgressEntity> __insertionAdapterOfDailyProgressEntity;

  private final EntityInsertionAdapter<UserProfileEntity> __insertionAdapterOfUserProfileEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public SessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSessionEntity = new EntityInsertionAdapter<SessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `sessions` (`id`,`timestampIso`,`exercise`,`reps`,`durationSeconds`,`totalXp`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SessionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTimestampIso());
        statement.bindString(3, entity.getExercise());
        statement.bindLong(4, entity.getReps());
        statement.bindDouble(5, entity.getDurationSeconds());
        statement.bindLong(6, entity.getTotalXp());
      }
    };
    this.__insertionAdapterOfDailyProgressEntity = new EntityInsertionAdapter<DailyProgressEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `daily_progress` (`date`,`pushups`,`squats`,`plankSeconds`,`bicepLeft`,`bicepRight`,`goalsMet`,`lastUpdatedIso`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DailyProgressEntity entity) {
        statement.bindString(1, entity.getDate());
        statement.bindLong(2, entity.getPushups());
        statement.bindLong(3, entity.getSquats());
        statement.bindLong(4, entity.getPlankSeconds());
        statement.bindLong(5, entity.getBicepLeft());
        statement.bindLong(6, entity.getBicepRight());
        final int _tmp = entity.getGoalsMet() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindString(8, entity.getLastUpdatedIso());
      }
    };
    this.__insertionAdapterOfUserProfileEntity = new EntityInsertionAdapter<UserProfileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `user_profile` (`id`,`totalXp`,`name`) VALUES (?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final UserProfileEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTotalXp());
        statement.bindString(3, entity.getName());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM sessions WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final SessionEntity session, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSessionEntity.insertAndReturnId(session);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertDaily(final DailyProgressEntity progress,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDailyProgressEntity.insert(progress);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertProfile(final UserProfileEntity profile,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfUserProfileEntity.insert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getAll(final Continuation<? super List<SessionEntity>> $completion) {
    final String _sql = "SELECT * FROM sessions ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SessionEntity>>() {
      @Override
      @NonNull
      public List<SessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestampIso = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampIso");
          final int _cursorIndexOfExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "exercise");
          final int _cursorIndexOfReps = CursorUtil.getColumnIndexOrThrow(_cursor, "reps");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final int _cursorIndexOfTotalXp = CursorUtil.getColumnIndexOrThrow(_cursor, "totalXp");
          final List<SessionEntity> _result = new ArrayList<SessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SessionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTimestampIso;
            _tmpTimestampIso = _cursor.getString(_cursorIndexOfTimestampIso);
            final String _tmpExercise;
            _tmpExercise = _cursor.getString(_cursorIndexOfExercise);
            final int _tmpReps;
            _tmpReps = _cursor.getInt(_cursorIndexOfReps);
            final float _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getFloat(_cursorIndexOfDurationSeconds);
            final int _tmpTotalXp;
            _tmpTotalXp = _cursor.getInt(_cursorIndexOfTotalXp);
            _item = new SessionEntity(_tmpId,_tmpTimestampIso,_tmpExercise,_tmpReps,_tmpDurationSeconds,_tmpTotalXp);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final long id, final Continuation<? super SessionEntity> $completion) {
    final String _sql = "SELECT * FROM sessions WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SessionEntity>() {
      @Override
      @Nullable
      public SessionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestampIso = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampIso");
          final int _cursorIndexOfExercise = CursorUtil.getColumnIndexOrThrow(_cursor, "exercise");
          final int _cursorIndexOfReps = CursorUtil.getColumnIndexOrThrow(_cursor, "reps");
          final int _cursorIndexOfDurationSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "durationSeconds");
          final int _cursorIndexOfTotalXp = CursorUtil.getColumnIndexOrThrow(_cursor, "totalXp");
          final SessionEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTimestampIso;
            _tmpTimestampIso = _cursor.getString(_cursorIndexOfTimestampIso);
            final String _tmpExercise;
            _tmpExercise = _cursor.getString(_cursorIndexOfExercise);
            final int _tmpReps;
            _tmpReps = _cursor.getInt(_cursorIndexOfReps);
            final float _tmpDurationSeconds;
            _tmpDurationSeconds = _cursor.getFloat(_cursorIndexOfDurationSeconds);
            final int _tmpTotalXp;
            _tmpTotalXp = _cursor.getInt(_cursorIndexOfTotalXp);
            _result = new SessionEntity(_tmpId,_tmpTimestampIso,_tmpExercise,_tmpReps,_tmpDurationSeconds,_tmpTotalXp);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM sessions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTotalRepsForExercise(final String exercise,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT SUM(reps) FROM sessions WHERE exercise = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, exercise);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getTotalXp(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT SUM(totalXp) FROM sessions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @Nullable
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final Integer _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getInt(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getDaily(final String date,
      final Continuation<? super DailyProgressEntity> $completion) {
    final String _sql = "SELECT * FROM daily_progress WHERE date = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, date);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DailyProgressEntity>() {
      @Override
      @Nullable
      public DailyProgressEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfPushups = CursorUtil.getColumnIndexOrThrow(_cursor, "pushups");
          final int _cursorIndexOfSquats = CursorUtil.getColumnIndexOrThrow(_cursor, "squats");
          final int _cursorIndexOfPlankSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "plankSeconds");
          final int _cursorIndexOfBicepLeft = CursorUtil.getColumnIndexOrThrow(_cursor, "bicepLeft");
          final int _cursorIndexOfBicepRight = CursorUtil.getColumnIndexOrThrow(_cursor, "bicepRight");
          final int _cursorIndexOfGoalsMet = CursorUtil.getColumnIndexOrThrow(_cursor, "goalsMet");
          final int _cursorIndexOfLastUpdatedIso = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedIso");
          final DailyProgressEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDate;
            _tmpDate = _cursor.getString(_cursorIndexOfDate);
            final int _tmpPushups;
            _tmpPushups = _cursor.getInt(_cursorIndexOfPushups);
            final int _tmpSquats;
            _tmpSquats = _cursor.getInt(_cursorIndexOfSquats);
            final int _tmpPlankSeconds;
            _tmpPlankSeconds = _cursor.getInt(_cursorIndexOfPlankSeconds);
            final int _tmpBicepLeft;
            _tmpBicepLeft = _cursor.getInt(_cursorIndexOfBicepLeft);
            final int _tmpBicepRight;
            _tmpBicepRight = _cursor.getInt(_cursorIndexOfBicepRight);
            final boolean _tmpGoalsMet;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfGoalsMet);
            _tmpGoalsMet = _tmp != 0;
            final String _tmpLastUpdatedIso;
            _tmpLastUpdatedIso = _cursor.getString(_cursorIndexOfLastUpdatedIso);
            _result = new DailyProgressEntity(_tmpDate,_tmpPushups,_tmpSquats,_tmpPlankSeconds,_tmpBicepLeft,_tmpBicepRight,_tmpGoalsMet,_tmpLastUpdatedIso);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getRecentDaily(final int limit,
      final Continuation<? super List<DailyProgressEntity>> $completion) {
    final String _sql = "SELECT * FROM daily_progress ORDER BY date DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DailyProgressEntity>>() {
      @Override
      @NonNull
      public List<DailyProgressEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final int _cursorIndexOfPushups = CursorUtil.getColumnIndexOrThrow(_cursor, "pushups");
          final int _cursorIndexOfSquats = CursorUtil.getColumnIndexOrThrow(_cursor, "squats");
          final int _cursorIndexOfPlankSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "plankSeconds");
          final int _cursorIndexOfBicepLeft = CursorUtil.getColumnIndexOrThrow(_cursor, "bicepLeft");
          final int _cursorIndexOfBicepRight = CursorUtil.getColumnIndexOrThrow(_cursor, "bicepRight");
          final int _cursorIndexOfGoalsMet = CursorUtil.getColumnIndexOrThrow(_cursor, "goalsMet");
          final int _cursorIndexOfLastUpdatedIso = CursorUtil.getColumnIndexOrThrow(_cursor, "lastUpdatedIso");
          final List<DailyProgressEntity> _result = new ArrayList<DailyProgressEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DailyProgressEntity _item;
            final String _tmpDate;
            _tmpDate = _cursor.getString(_cursorIndexOfDate);
            final int _tmpPushups;
            _tmpPushups = _cursor.getInt(_cursorIndexOfPushups);
            final int _tmpSquats;
            _tmpSquats = _cursor.getInt(_cursorIndexOfSquats);
            final int _tmpPlankSeconds;
            _tmpPlankSeconds = _cursor.getInt(_cursorIndexOfPlankSeconds);
            final int _tmpBicepLeft;
            _tmpBicepLeft = _cursor.getInt(_cursorIndexOfBicepLeft);
            final int _tmpBicepRight;
            _tmpBicepRight = _cursor.getInt(_cursorIndexOfBicepRight);
            final boolean _tmpGoalsMet;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfGoalsMet);
            _tmpGoalsMet = _tmp != 0;
            final String _tmpLastUpdatedIso;
            _tmpLastUpdatedIso = _cursor.getString(_cursorIndexOfLastUpdatedIso);
            _item = new DailyProgressEntity(_tmpDate,_tmpPushups,_tmpSquats,_tmpPlankSeconds,_tmpBicepLeft,_tmpBicepRight,_tmpGoalsMet,_tmpLastUpdatedIso);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getProfile(final Continuation<? super UserProfileEntity> $completion) {
    final String _sql = "SELECT * FROM user_profile WHERE id = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<UserProfileEntity>() {
      @Override
      @Nullable
      public UserProfileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTotalXp = CursorUtil.getColumnIndexOrThrow(_cursor, "totalXp");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final UserProfileEntity _result;
          if (_cursor.moveToFirst()) {
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final int _tmpTotalXp;
            _tmpTotalXp = _cursor.getInt(_cursorIndexOfTotalXp);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            _result = new UserProfileEntity(_tmpId,_tmpTotalXp,_tmpName);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
