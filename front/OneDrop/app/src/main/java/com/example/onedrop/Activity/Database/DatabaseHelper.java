package com.example.onedrop.Activity.Database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "onedrop_local.db";
    private static final int DATABASE_VERSION = 1;

    // Users Table
    public static final String TABLE_USERS = "users";
    public static final String COL_USER_ID = "user_id";
    public static final String COL_NAME = "name";
    public static final String COL_EMAIL = "email";
    public static final String COL_BLOOD_TYPE = "blood_type";
    public static final String COL_GENDER = "gender";
    public static final String COL_MOBILE = "mobile";
    public static final String COL_PROFILE_IMAGE = "profile_image";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_PROFILE_COMPLETE = "profile_complete";
    public static final String COL_LAST_SYNC = "last_sync";
    public static final String COL_SYNCED = "synced";

    // Home Data Table
    public static final String TABLE_HOME_DATA = "home_data";
    public static final String COL_DONATION_COUNT = "donation_count";
    public static final String COL_LIVES_SAVED = "lives_saved";
    public static final String COL_ALERT_COUNT = "alert_count";

    // Alerts Table
    public static final String TABLE_ALERTS = "alerts";
    public static final String COL_ALERT_ID = "alert_id";
    public static final String COL_ALERT_TITLE = "alert_title";
    public static final String COL_ALERT_MESSAGE = "alert_message";
    public static final String COL_ALERT_TIME = "alert_time";
    public static final String COL_ALERT_READ = "is_read";

    // Leaderboard Table
    public static final String TABLE_LEADERBOARD = "leaderboard";
    public static final String COL_RANK = "rank";
    public static final String COL_TOTAL_POINTS = "total_points";

    // Donations Table
    public static final String TABLE_DONATIONS = "donations";
    public static final String COL_DONATION_ID = "donation_id";
    public static final String COL_AMOUNT = "amount";
    public static final String COL_DONATED_AT = "donated_at";
    public static final String COL_PAYMENT_STATUS = "payment_status";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Users Table
        String CREATE_USERS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_USERS + " (" +
                COL_USER_ID + " INTEGER PRIMARY KEY," +
                COL_NAME + " TEXT," +
                COL_EMAIL + " TEXT UNIQUE," +
                COL_BLOOD_TYPE + " TEXT," +
                COL_GENDER + " TEXT," +
                COL_MOBILE + " TEXT," +
                COL_PROFILE_IMAGE + " TEXT," +
                COL_CREATED_AT + " TEXT," +
                COL_PROFILE_COMPLETE + " INTEGER DEFAULT 0," +
                COL_LAST_SYNC + " INTEGER DEFAULT 0," +
                COL_SYNCED + " INTEGER DEFAULT 0" +
                ")";
        db.execSQL(CREATE_USERS_TABLE);

        // Create Home Data Table
        String CREATE_HOME_DATA_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_HOME_DATA + " (" +
                COL_USER_ID + " INTEGER PRIMARY KEY," +
                COL_DONATION_COUNT + " INTEGER DEFAULT 0," +
                COL_LIVES_SAVED + " INTEGER DEFAULT 0," +
                COL_ALERT_COUNT + " INTEGER DEFAULT 0," +
                COL_LAST_SYNC + " INTEGER DEFAULT 0," +
                "FOREIGN KEY(" + COL_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COL_USER_ID + ")" +
                ")";
        db.execSQL(CREATE_HOME_DATA_TABLE);

        // Create Alerts Table
        String CREATE_ALERTS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_ALERTS + " (" +
                COL_ALERT_ID + " INTEGER PRIMARY KEY," +
                COL_USER_ID + " INTEGER," +
                COL_ALERT_TITLE + " TEXT," +
                COL_ALERT_MESSAGE + " TEXT," +
                COL_ALERT_TIME + " TEXT," +
                COL_ALERT_READ + " INTEGER DEFAULT 0," +
                COL_LAST_SYNC + " INTEGER DEFAULT 0," +
                "FOREIGN KEY(" + COL_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COL_USER_ID + ")" +
                ")";
        db.execSQL(CREATE_ALERTS_TABLE);

        // Create Leaderboard Table
        String CREATE_LEADERBOARD_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_LEADERBOARD + " (" +
                COL_USER_ID + " INTEGER PRIMARY KEY," +
                COL_RANK + " INTEGER," +
                COL_TOTAL_POINTS + " INTEGER DEFAULT 0," +
                COL_LAST_SYNC + " INTEGER DEFAULT 0," +
                "FOREIGN KEY(" + COL_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COL_USER_ID + ")" +
                ")";
        db.execSQL(CREATE_LEADERBOARD_TABLE);

        // Create Donations Table
        String CREATE_DONATIONS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_DONATIONS + " (" +
                COL_DONATION_ID + " INTEGER PRIMARY KEY," +
                COL_USER_ID + " INTEGER," +
                COL_AMOUNT + " REAL," +
                COL_DONATED_AT + " TEXT," +
                COL_PAYMENT_STATUS + " TEXT," +
                COL_LAST_SYNC + " INTEGER DEFAULT 0," +
                "FOREIGN KEY(" + COL_USER_ID + ") REFERENCES " + TABLE_USERS + "(" + COL_USER_ID + ")" +
                ")";
        db.execSQL(CREATE_DONATIONS_TABLE);

        // Create Indexes for faster queries
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_email ON " + TABLE_USERS + "(" + COL_EMAIL + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_alerts_user ON " + TABLE_ALERTS + "(" + COL_USER_ID + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_donations_user ON " + TABLE_DONATIONS + "(" + COL_USER_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOME_DATA);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALERTS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LEADERBOARD);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_DONATIONS);
            onCreate(db);
        }
    }

    public void clearAllData(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_ALERTS, COL_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        db.delete(TABLE_HOME_DATA, COL_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        db.delete(TABLE_LEADERBOARD, COL_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        db.delete(TABLE_DONATIONS, COL_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        db.delete(TABLE_USERS, COL_USER_ID + " = ?", new String[]{String.valueOf(userId)});
        db.close();
    }
}