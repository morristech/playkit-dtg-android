package com.kaltura.dtg.imp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.kaltura.dtg.DownloadItem;
import com.kaltura.dtg.DownloadState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

class Database {
    private static final int DB_VERSION = 3;
    private static final String TBL_DOWNLOAD_FILES = "Files";
    private static final String COL_FILE_URL = "FileURL";
    private static final String COL_TARGET_FILE = "TargetFile";
    private static final String TBL_ITEMS = "Items";
    private static final String COL_ITEM_ID = "ItemID";
    private static final String COL_CONTENT_URL = "ContentURL";
    private static final String COL_FILE_ORDER = "OrderInTrack";
    static final String COL_ITEM_STATE = "ItemState";
    private static final String COL_ITEM_ADD_TIME = "TimeAdded";
    private static final String COL_ITEM_FINISH_TIME = "TimeFinished";
    private static final String COL_ITEM_DATA_DIR = "ItemDataDir";
    static final String COL_ITEM_ESTIMATED_SIZE = "ItemEstimatedSize";
    static final String COL_ITEM_DOWNLOADED_SIZE = "ItemDownloadedSize";
    static final String COL_ITEM_PLAYBACK_PATH = "ItemPlaybackPath";
    static final String COL_ITEM_DURATION = "ItemDuration";
    private static final String[] ALL_ITEM_COLS = new String[]{COL_ITEM_ID, COL_CONTENT_URL,
            COL_ITEM_STATE, COL_ITEM_ADD_TIME, COL_ITEM_ESTIMATED_SIZE, COL_ITEM_DOWNLOADED_SIZE,
            COL_ITEM_PLAYBACK_PATH, COL_ITEM_DATA_DIR, COL_ITEM_DURATION};
    private static final String TAG = "Database";
    private static final String TBL_TRACK = "Track";
    static final String COL_TRACK_ID = "TrackId";
    static final String COL_TRACK_EXTRA = "TrackExtra";
    private static final String COL_TRACK_STATE = "TrackState";
    static final String COL_TRACK_TYPE = "TrackType";
    static final String COL_TRACK_LANGUAGE = "TrackLanguage";
    static final String COL_TRACK_BITRATE = "TrackBitrate";
    static final String COL_TRACK_REL_ID = "TrackRelativeId";
    static final String COL_TRACK_CODECS = "TrackCodecs";
    private static final String COL_FILE_COMPLETE = "FileComplete";

    private static final String EXTFILES_SCHEME = "extfiles";

    private final SQLiteOpenHelper helper;
    private final SQLiteDatabase database;
    private final String externalFilesDir;

    private BufferedWriter traceWriter;
    private long start;// = SystemClock.elapsedRealtime();

    private static String createTable(String name, String... colDefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(name).append("(");
        for (int i = 0; i < colDefs.length; i += 2) {
            if (i > 0) {
                sb.append(",\n");
            }
            sb.append(colDefs[i]).append(" ").append(colDefs[i + 1]);
        }
        sb.append(");");
        String str = sb.toString();
        Log.i("DBUtils", "Create table:\n" + str);
        return str;
    }

    private static String createUniqueIndex(String tableName, String... colNames) {

        String str = "CREATE UNIQUE INDEX " +
                "unique_" + tableName + "_" + TextUtils.join("_", colNames) +
                " ON " + tableName +
                " (" + TextUtils.join(",", colNames) + ");";

        Log.i("DBUtils", "Create index:\n" + str);
        return str;
    }

    private void trace(String funcName, Object... args) {
        if (start == 0) return;
        try {
            traceWriter.append(String.valueOf(SystemClock.elapsedRealtime() - start));
            traceWriter.append(' ');
            traceWriter.append(funcName).append('\t');
            for (Object arg : args) {
                if (arg instanceof Object[]) {
                    traceWriter.append('[');
                    for (Object s : ((Object[]) arg)) {
                        traceWriter.append(String.valueOf(s));

                    }
                    traceWriter.append(']');
                } else {
                    traceWriter.append(String.valueOf(arg));
                }
                traceWriter.append(' ');
            }
            traceWriter.newLine();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }


    Database(File dbFile, final Context context) {

        this.externalFilesDir = Storage.getExtFilesDir().getAbsolutePath();

        try {
            traceWriter = new BufferedWriter(new FileWriter(dbFile.getParent() + "/dbtrace.txt"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        helper = new SQLiteOpenHelper(context, dbFile.getAbsolutePath(), null, DB_VERSION) {

            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(createTable(
                        TBL_ITEMS,
                        COL_ITEM_ID, "TEXT PRIMARY KEY",
                        COL_CONTENT_URL, "TEXT NOT NULL",
                        COL_ITEM_STATE, "TEXT NOT NULL",
                        COL_ITEM_ADD_TIME, "INTEGER NOT NULL",
                        COL_ITEM_FINISH_TIME, "INTEGER NOT NULL DEFAULT 0",
                        COL_ITEM_DATA_DIR, "TEXT NOT NULL",
                        COL_ITEM_ESTIMATED_SIZE, "INTEGER NOT NULL DEFAULT 0",
                        COL_ITEM_DOWNLOADED_SIZE, "INTEGER NOT NULL DEFAULT 0",
                        COL_ITEM_PLAYBACK_PATH, "TEXT",
                        COL_ITEM_DURATION, "INTEGER"
                ));

                createFilesTable(db);

                createTrackTable(db);
            }

            private void createFilesTable(SQLiteDatabase db) {
                db.execSQL(createTable(
                        TBL_DOWNLOAD_FILES,
                        COL_ITEM_ID, "TEXT NOT NULL REFERENCES " + TBL_ITEMS + "(" + COL_ITEM_ID + ") ON DELETE CASCADE",
                        COL_FILE_URL, "TEXT NOT NULL",
                        COL_TARGET_FILE, "TEXT NOT NULL",
                        COL_TRACK_REL_ID, "TEXT",
                        COL_FILE_COMPLETE, "INTEGER NOT NULL DEFAULT 0",
                        COL_FILE_ORDER, "INTEGER"
                ));
                db.execSQL(createUniqueIndex(TBL_DOWNLOAD_FILES, COL_ITEM_ID, COL_FILE_URL));
            }

            private void createTrackTable(SQLiteDatabase db) {
                db.execSQL(createTable(
                        TBL_TRACK,
                        COL_TRACK_ID, "INTEGER PRIMARY KEY",
                        COL_TRACK_STATE, "TEXT NOT NULL",   // DashDownloader.TrackState
                        COL_TRACK_TYPE, "TEXT NOT NULL",    // DownloadItem.TrackType
                        COL_TRACK_LANGUAGE, "TEXT",
                        COL_TRACK_BITRATE, "INTEGER",
                        COL_TRACK_REL_ID, "TEXT NOT NULL",
                        COL_TRACK_EXTRA, "TEXT",
                        COL_TRACK_CODECS, "TEXT",
                        COL_ITEM_ID, "TEXT NOT NULL REFERENCES " + TBL_ITEMS + "(" + COL_ITEM_ID + ") ON DELETE CASCADE"
                ));
                db.execSQL(createUniqueIndex(TBL_TRACK, COL_ITEM_ID, COL_TRACK_REL_ID));
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                db.beginTransaction();

                if (oldVersion < 2) {
                    // Upgrade 1 -> 2: Track table was missing
                    createTrackTable(db);

                    // recreate Files table
                    db.execSQL("DROP INDEX IF EXISTS unique_Files_ItemID_FileURL");
                    db.execSQL(Utils.format("ALTER TABLE %s RENAME TO OLD_%s", TBL_DOWNLOAD_FILES, TBL_DOWNLOAD_FILES));
                    createFilesTable(db);

                    db.execSQL(Utils.format("INSERT INTO %s(%s,%s,%s) SELECT %s, %s, %s FROM %s",
                            TBL_DOWNLOAD_FILES, COL_ITEM_ID, COL_FILE_URL, COL_TARGET_FILE,
                            COL_ITEM_ID, COL_FILE_URL, COL_TARGET_FILE, TBL_DOWNLOAD_FILES));
                    db.execSQL(Utils.format("DROP TABLE OLD_%s", TBL_DOWNLOAD_FILES));
                }

                if (oldVersion < 3) {
                    // Assuming v2 or just finished upgrade to v2

                    // Add COL_FILE_ORDER to files
                    db.execSQL(Utils.format("ALTER TABLE %s ADD COLUMN %s INTEGER", TBL_DOWNLOAD_FILES, COL_FILE_ORDER));

                    // Add duration to items
                    db.execSQL(Utils.format("ALTER TABLE %s ADD COLUMN %s TEXT", TBL_ITEMS, COL_ITEM_DURATION));

                    // Add codecs to tracks
                    db.execSQL(Utils.format("ALTER TABLE %s ADD COLUMN %s TEXT", TBL_TRACK, COL_TRACK_CODECS));

                    changeTargetFileToRelative(db);
                }

                db.setTransactionSuccessful();
                db.endTransaction();
            }

            @Override
            public void onConfigure(SQLiteDatabase db) {
                super.onConfigure(db);
                db.setForeignKeyConstraintsEnabled(true);
                db.setLocale(Locale.US);
            }
        };
        database = helper.getWritableDatabase();
    }

    // Only to be called from onUpgrade()
    private void changeTargetFileToRelative(SQLiteDatabase db) {
        final String sql = Utils.format("UPDATE %s SET %s = replace(%s, ?, ?)",
                TBL_DOWNLOAD_FILES, COL_TARGET_FILE, COL_TARGET_FILE);
        db.execSQL(sql, new String[]{externalFilesDir, EXTFILES_SCHEME + ":///"});
    }

    private static void safeClose(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    private static String[] strings(String... strings) {
        return strings;
    }

    synchronized private void doTransaction(Transaction transaction) {
        if (database == null) {
            return;
        }

        boolean success;
        try {
            database.beginTransaction();

            success = transaction.execute(database);

            if (success) {
                database.setTransactionSuccessful();
            }
        } finally {
            database.endTransaction();
        }
    }

    synchronized void close() {
        database.close();
        helper.close();
    }

    synchronized void addDownloadTasksToDB(final DownloadItem item, final List<DownloadTask> downloadTasks) {
        trace("addDownloadTasksToDB", item.getItemId(), downloadTasks.size());

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                for (DownloadTask task : downloadTasks) {
                    values.put(COL_ITEM_ID, item.getItemId());
                    values.put(COL_FILE_URL, task.url.toString());
                    values.put(COL_TARGET_FILE, relativeExtFilesPath(task.targetFile));
                    values.put(COL_TRACK_REL_ID, task.trackRelativeId);
                    values.put(COL_FILE_ORDER, task.order);
                    try {
                        long rowid = db.insertWithOnConflict(TBL_DOWNLOAD_FILES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                        if (rowid <= 0) {
//                            Log.d(TAG, "Warning: task not added:" + task.targetFile);
                        }
                    } catch (SQLException e) {
                        Log.e(TAG, "Failed to INSERT task: " + task.targetFile, e);
                    }
                }
                return true;
            }
        });
    }

    synchronized ArrayList<DownloadTask> readPendingDownloadTasksFromDB(final String itemId) {
        trace("readPendingDownloadTasksFromDB", itemId);

        final ArrayList<DownloadTask> downloadTasks = new ArrayList<>();

        Cursor cursor = null;

        try {
            cursor = database.query(TBL_DOWNLOAD_FILES, new String[]{COL_FILE_URL, COL_TARGET_FILE, COL_FILE_ORDER},
                    COL_ITEM_ID + "==? AND " + COL_FILE_COMPLETE + "==0", new String[]{itemId}, null, null, COL_FILE_ORDER);

            while (cursor.moveToNext()) {
                String url = cursor.getString(0);
                String file = cursor.getString(1);
                int order = cursor.isNull(2) ? DownloadTask.UNKNOWN_ORDER : cursor.getInt(2);

                File targetFile = absoluteExtFilesFile(file);
                DownloadTask task = new DownloadTask(Uri.parse(url), targetFile, order);
                task.itemId = itemId;

                downloadTasks.add(task);
            }

        } finally {
            safeClose(cursor);
        }

        return downloadTasks;
    }

    @NonNull
    private File absoluteExtFilesFile(String file) {
        if (file.startsWith("/")) return new File(file);    // already absolute

        final Uri uri = Uri.parse(file);

        if (TextUtils.equals(uri.getScheme(), EXTFILES_SCHEME)) {
            return new File(externalFilesDir, uri.getPath());
        }

        throw new IllegalArgumentException("Can't resolve filename " + file);
    }

    private String relativeExtFilesPath(File targetFile) {
        final String absolutePath = targetFile.getAbsolutePath();

        if (absolutePath.startsWith(externalFilesDir)) {
            return absolutePath.replace(externalFilesDir, EXTFILES_SCHEME + ":///");
        }

        throw new IllegalArgumentException("Can't convert filename " + targetFile);
    }

    synchronized void markTaskAsComplete(final DownloadTask downloadTask) {
        trace("markTaskAsComplete", downloadTask.itemId, downloadTask.taskId);

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_FILE_COMPLETE, 1);

                db.updateWithOnConflict(TBL_DOWNLOAD_FILES, values, COL_TARGET_FILE + "==?",
                        new String[]{relativeExtFilesPath(downloadTask.targetFile)},
                        SQLiteDatabase.CONFLICT_IGNORE);
                return true;
            }
        });

        trace("markTaskAsComplete done", downloadTask.itemId, downloadTask.taskId);
    }

    synchronized DownloadItemImp findItemInDB(String itemId) {

        trace("findItemInDB", itemId);

        Cursor cursor = null;
        DownloadItemImp item = null;

        try {
            cursor = database.query(TBL_ITEMS,
                    ALL_ITEM_COLS,
                    COL_ITEM_ID + "==?", new String[]{itemId}, null, null, null);

            if (cursor.moveToFirst()) {
                item = readItem(cursor);
            }

        } finally {
            safeClose(cursor);
        }

        return item;
    }

    synchronized void addItemToDB(final DownloadItemImp item, final File itemDataDir) {
        trace("addItemToDB", item.getItemId());

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues(5);
                values.put(COL_ITEM_ID, item.getItemId());
                values.put(COL_CONTENT_URL, item.getContentURL());
                values.put(COL_ITEM_ADD_TIME, item.getAddedTime());
                values.put(COL_ITEM_STATE, item.getState().name());
                values.put(COL_ITEM_DATA_DIR, itemDataDir.getAbsolutePath());
                values.put(COL_ITEM_PLAYBACK_PATH, item.getPlaybackPath());
                values.put(COL_ITEM_DURATION, item.getDurationMS());
                db.insert(TBL_ITEMS, null, values);
                return true;
            }
        });
    }

    synchronized void removeItemFromDB(final String itemId) {

        trace("removeItemFromDB", itemId);

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                db.delete(TBL_ITEMS, COL_ITEM_ID + "=?", new String[]{itemId});

                // There's an "on delete cascade" between TBL_ITEMS and TBL_DOWNLOAD_FILES,
                // but it wasn't active in the previous schema.
                db.delete(TBL_DOWNLOAD_FILES, COL_ITEM_ID + "=?", new String[]{itemId});
                return true;
            }
        });
    }

    synchronized void updateItemState(final String itemId, final DownloadState itemState) {
        trace("updateItemState", itemId, itemState);

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_STATE, itemState.name());

                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});

                return true;
            }
        });
    }

    synchronized void setDownloadFinishTime(final String itemId) {
        trace("setDownloadFinishTime", itemId);

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_FINISH_TIME, System.currentTimeMillis());

                int res = db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});

                return res > 0;
            }
        });
    }

    synchronized void setEstimatedSize(final String itemId, final long estimatedSizeBytes) {
        trace("setEstimatedSize", itemId);

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_ESTIMATED_SIZE, estimatedSizeBytes);
                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});
                return true;
            }
        });
    }

    synchronized void updateDownloadedFileSize(final String itemId, final long downloadedFileSize) {
        trace("updateDownloadedFileSize", itemId);

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues();
                values.put(COL_ITEM_DOWNLOADED_SIZE, downloadedFileSize);
                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});
                return true;
            }
        });
    }

    // If itemId is null, sum all items.
    long getEstimatedItemSize(@Nullable String itemId) {
        return getItemColumnLong(itemId, COL_ITEM_ESTIMATED_SIZE);
    }

    long getDownloadedItemSize(@Nullable String itemId) {
        return getItemColumnLong(itemId, COL_ITEM_DOWNLOADED_SIZE);
    }

    // If itemId is null, sum all items.
    private synchronized long getItemColumnLong(@Nullable String itemId, @NonNull String col) {
        trace("getItemColumnLong", itemId, col);

        SQLiteDatabase db = database;
        Cursor cursor = null;
        try {
            if (itemId != null) {
                cursor = db.query(TBL_ITEMS, new String[]{col}, COL_ITEM_ID + "==?", new String[]{itemId}, null, null, null);
            } else {
                cursor = db.rawQuery("SELECT SUM(" + col + ") FROM " + TBL_ITEMS, null);
            }
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
            return 0;
        } finally {
            safeClose(cursor);
        }
    }

    synchronized void updateItemInfo(final DownloadItemImp item, final String[] columns) {
        final String itemId = item.getItemId();
        trace("updateItemInfo", itemId, columns);

        if (columns == null || columns.length == 0) {
            throw new IllegalArgumentException("columns.length must be >0");
        }
        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {
                ContentValues values = new ContentValues(columns.length);
                for (String column : columns) {
                    switch (column) {
                        case COL_ITEM_ADD_TIME:
                            values.put(COL_ITEM_ADD_TIME, item.getAddedTime());
                            break;
                        case COL_ITEM_STATE:
                            values.put(COL_ITEM_STATE, item.getState().name());
                            break;
                        case COL_ITEM_ESTIMATED_SIZE:
                            values.put(COL_ITEM_ESTIMATED_SIZE, item.getEstimatedSizeBytes());
                            break;
                        case COL_ITEM_DOWNLOADED_SIZE:
                            values.put(COL_ITEM_DOWNLOADED_SIZE, item.getDownloadedSizeBytes());
                            break;
                        case COL_ITEM_PLAYBACK_PATH:
                            values.put(COL_ITEM_PLAYBACK_PATH, item.getPlaybackPath());
                            break;
                        case COL_ITEM_DATA_DIR:
                            values.put(COL_ITEM_DATA_DIR, item.getDataDir());
                            break;
                        case COL_ITEM_DURATION:
                            values.put(COL_ITEM_DURATION, item.getDurationMS());
                            break;

                        // invalid -- can't change those. 
                        case COL_ITEM_ID:
                        case COL_CONTENT_URL:
                            return false;   // fail the transaction
                    }
                }
                if (values.size() == 0) {
                    Log.e(TAG, "No values; columns=" + Arrays.toString(columns));
                    return false;
                }
                db.update(TBL_ITEMS, values, COL_ITEM_ID + "==?", new String[]{itemId});
                return true;
            }
        });
        trace("updateItemInfo done", itemId, columns);
    }

    private DownloadItemImp readItem(Cursor cursor) {

        String[] columns = cursor.getColumnNames();

        // the bare minimum: itemId and contentURL
        String itemId = cursor.getString(cursor.getColumnIndexOrThrow(COL_ITEM_ID));
        String contentURL = cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT_URL));

        DownloadItemImp item = new DownloadItemImp(itemId, contentURL);
        for (int i = 0; i < columns.length; i++) {
            switch (columns[i]) {
                case COL_ITEM_ID:
                case COL_CONTENT_URL:
                    // we already have those. 
                    break;
                case COL_ITEM_STATE:
                    item.setState(DownloadState.valueOf(cursor.getString(i)));
                    break;
                case COL_ITEM_ESTIMATED_SIZE:
                    item.setEstimatedSizeBytes(cursor.getLong(i));
                    break;
                case COL_ITEM_DOWNLOADED_SIZE:
                    item.setDownloadedSizeBytes(cursor.getLong(i));
                    break;
                case COL_ITEM_ADD_TIME:
                    item.setAddedTime(cursor.getLong(i));
                    break;
                case COL_ITEM_DATA_DIR:
                    item.setDataDir(cursor.getString(i));
                    break;
                case COL_ITEM_PLAYBACK_PATH:
                    item.setPlaybackPath(cursor.getString(i));
                    break;
                case COL_ITEM_FINISH_TIME:
                    item.setFinishedTime(cursor.getLong(i));
                    break;
                case COL_ITEM_DURATION:
                    item.setDurationMS(cursor.getLong(i));
                    break;
            }
        }
        return item;
    }

    synchronized ArrayList<DownloadItemImp> readItemsFromDB(DownloadState[] states) {
        trace("readItemsFromDB", (Object) states);

        String stateNames[] = new String[states.length];
        for (int i = 0; i < states.length; i++) {
            stateNames[i] = states[i].name();
        }
        String placeholders = "(" + TextUtils.join(",", Collections.nCopies(stateNames.length, "?")) + ")";

        ArrayList<DownloadItemImp> items = new ArrayList<>();

        Cursor cursor = null;

        try {
            cursor = database.query(TBL_ITEMS,
                    ALL_ITEM_COLS,
                    COL_ITEM_STATE + " IN " + placeholders, stateNames, null, null, null);

            while (cursor.moveToNext()) {
                DownloadItemImp item = readItem(cursor);
                items.add(item);
            }
        } finally {
            safeClose(cursor);
        }


        return items;
    }

    synchronized int countPendingFiles(String itemId, @Nullable String trackId) {

        trace("countPendingFiles", itemId, trackId);

        SQLiteDatabase db = database;
        Cursor cursor = null;
        int count = 0;

        try {
            if (trackId != null) {
                String sql = "SELECT COUNT(*) FROM " + TBL_DOWNLOAD_FILES +
                        " WHERE " + COL_ITEM_ID + "==? AND " + COL_FILE_COMPLETE + "==0 AND " + COL_TRACK_REL_ID + "==?";
                cursor = db.rawQuery(sql, new String[]{itemId, trackId});

            } else {
                String sql = "SELECT COUNT(*) FROM " + TBL_DOWNLOAD_FILES +
                        " WHERE " + COL_ITEM_ID + "==? AND " + COL_FILE_COMPLETE + "==0";
                cursor = db.rawQuery(sql, new String[]{itemId});
            }

            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

        } finally {
            safeClose(cursor);
        }

        return count;
    }

    synchronized void addTracks(final DownloadItemImp item, final List<BaseTrack> availableTracks, final List<BaseTrack> selectedTracks) {
        trace("addTracks", item.getItemId(), availableTracks.size(), selectedTracks.size());

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {

                for (BaseTrack track : availableTracks) {
                    ContentValues values = track.toContentValues();
                    values.put(COL_ITEM_ID, item.getItemId());
                    values.put(COL_TRACK_STATE, BaseTrack.TrackState.NOT_SELECTED.name());
                    try {
                        db.insertOrThrow(TBL_TRACK, null, values);
                    } catch (SQLiteConstraintException e) {
                        Log.w(TAG, "Insert failed", e);
                        Log.w(TAG, "execute: itemId=" + item.getItemId() + " rel=" + track.getRelativeId());
                    }
                }

                for (BaseTrack track : selectedTracks) {
                    ContentValues values = new ContentValues();
                    values.put(COL_TRACK_STATE, BaseTrack.TrackState.SELECTED.name());
                    db.update(TBL_TRACK, values, COL_ITEM_ID + "=? AND " + COL_TRACK_REL_ID + "=?",
                            strings(item.getItemId(), track.getRelativeId()));
                }

                return true;
            }
        });
    }

    synchronized List<BaseTrack> readTracks(String itemId, DownloadItem.TrackType type, @Nullable BaseTrack.TrackState state, AssetFormat assetFormat) {
        trace("readTracks", itemId, type, state, assetFormat);

        Cursor cursor = null;
        List<BaseTrack> tracks = new ArrayList<>(10);
        try {
            List<String> selectionCols = new ArrayList<>();
            List<String> selectionArgs = new ArrayList<>();

            selectionCols.add(COL_ITEM_ID);
            selectionArgs.add(itemId);

            if (type != null) {
                selectionCols.add(COL_TRACK_TYPE);
                selectionArgs.add(type.name());
            }

            if (state != null) {
                selectionCols.add(COL_TRACK_STATE);
                selectionArgs.add(state.name());
            }

            String selection = TextUtils.join("=? AND ", selectionCols) + "=?";
            String[] selectionArgsArray = selectionArgs.toArray(new String[0]);
            cursor = database.query(TBL_TRACK,
                    BaseTrack.REQUIRED_DB_FIELDS,
                    selection,
                    selectionArgsArray,
                    null, null, COL_TRACK_ID + " ASC");

            while (cursor.moveToNext()) {
                BaseTrack track = BaseTrack.create(cursor, assetFormat);
                tracks.add(track);
            }

        } finally {
            safeClose(cursor);
        }

        return tracks;
    }

    synchronized void updateTracksState(final String itemId, final List<BaseTrack> tracks, final BaseTrack.TrackState newState) {
        trace("updateTracksState", itemId, tracks.size(), newState);

        doTransaction(new Transaction() {
            @Override
            boolean execute(SQLiteDatabase db) {

                ContentValues values = new ContentValues();
                values.put(COL_TRACK_STATE, newState.name());

                for (BaseTrack track : tracks) {
                    final String whereClause = COL_ITEM_ID + "=? AND " + COL_TRACK_REL_ID + "=?";
                    final String[] whereArgs = strings(itemId, track.getRelativeId());
                    db.update(TBL_TRACK, values, whereClause, whereArgs);

                    if (newState == BaseTrack.TrackState.NOT_SELECTED) {
                        db.delete(TBL_DOWNLOAD_FILES, whereClause, whereArgs);
                    }
                }

                return true;
            }
        });
    }

    abstract static class Transaction {
        abstract boolean execute(SQLiteDatabase db);
    }

}
