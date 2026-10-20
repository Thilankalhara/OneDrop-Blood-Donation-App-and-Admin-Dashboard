package com.example.onedrop.Activity.Database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;

public class UserDataManager {

    private static final String TAG = "UserDataManager";
    private final DatabaseHelper dbHelper;
    private final SQLiteDatabase db;

    public UserDataManager(Context context) {
        dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    // ============ USER OPERATIONS ============

    public void saveUserData(int userId, String name, String email, String bloodType,
                             String gender, String mobile, String createdAt,
                             boolean profileComplete, String profileImage) {
        try {
            db.execSQL("INSERT OR REPLACE INTO " + DatabaseHelper.TABLE_USERS +
                            " (" + DatabaseHelper.COL_USER_ID + ", " +
                            DatabaseHelper.COL_NAME + ", " +
                            DatabaseHelper.COL_EMAIL + ", " +
                            DatabaseHelper.COL_BLOOD_TYPE + ", " +
                            DatabaseHelper.COL_GENDER + ", " +
                            DatabaseHelper.COL_MOBILE + ", " +
                            DatabaseHelper.COL_CREATED_AT + ", " +
                            DatabaseHelper.COL_PROFILE_COMPLETE + ", " +
                            DatabaseHelper.COL_PROFILE_IMAGE + ", " +
                            DatabaseHelper.COL_LAST_SYNC + ", " +
                            DatabaseHelper.COL_SYNCED +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{userId, name, email, bloodType, gender, mobile,
                            createdAt, profileComplete ? 1 : 0, profileImage,
                            System.currentTimeMillis(), 1});
            Log.d(TAG, "User data saved to SQLite: " + email);
        } catch (Exception e) {
            Log.e(TAG, "Error saving user data: " + e.getMessage());
        }
    }

    public JSONObject getUserData(int userId) {
        try {
            Cursor cursor = db.query(DatabaseHelper.TABLE_USERS,
                    null,
                    DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                JSONObject json = new JSONObject();
                json.put("user_id", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_USER_ID)));
                json.put("name", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_NAME)));
                json.put("email", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EMAIL)));
                json.put("blood_type", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_BLOOD_TYPE)));
                json.put("gender", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_GENDER)));
                json.put("mobile", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MOBILE)));
                json.put("created_at", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CREATED_AT)));
                json.put("profile_complete", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROFILE_COMPLETE)) == 1);
                json.put("profile_image", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PROFILE_IMAGE)));
                json.put("last_sync", cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LAST_SYNC)));
                json.put("synced", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SYNCED)) == 1);

                cursor.close();
                Log.d(TAG, "User data retrieved from SQLite");
                return json;
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving user data: " + e.getMessage());
        }
        return null;
    }

    // ============ HOME DATA OPERATIONS ============

    public void saveHomeData(int userId, int donationCount, int livesSaved) {
        try {
            db.execSQL("INSERT OR REPLACE INTO " + DatabaseHelper.TABLE_HOME_DATA +
                            " (" + DatabaseHelper.COL_USER_ID + ", " +
                            DatabaseHelper.COL_DONATION_COUNT + ", " +
                            DatabaseHelper.COL_LIVES_SAVED + ", " +
                            DatabaseHelper.COL_LAST_SYNC +
                            ") VALUES (?, ?, ?, ?)",
                    new Object[]{userId, donationCount, livesSaved, System.currentTimeMillis()});
            Log.d(TAG, "Home data saved: donations=" + donationCount);
        } catch (Exception e) {
            Log.e(TAG, "Error saving home data: " + e.getMessage());
        }
    }

    public JSONObject getHomeData(int userId) {
        try {
            Cursor cursor = db.query(DatabaseHelper.TABLE_HOME_DATA,
                    null,
                    DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                JSONObject json = new JSONObject();
                json.put("donation_count", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DONATION_COUNT)));
                json.put("lives_saved", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LIVES_SAVED)));
                json.put("last_sync", cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LAST_SYNC)));

                cursor.close();
                Log.d(TAG, "Home data retrieved from SQLite");
                return json;
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving home data: " + e.getMessage());
        }
        return null;
    }

    // ============ ALERTS OPERATIONS ============

    public void saveAlerts(int userId, String alertsJson) {
        try {
            // Clear old alerts
            db.delete(DatabaseHelper.TABLE_ALERTS, DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});

            // Insert new alerts
            org.json.JSONArray alerts = new org.json.JSONArray(alertsJson);
            for (int i = 0; i < alerts.length(); i++) {
                JSONObject alert = alerts.getJSONObject(i);
                db.execSQL("INSERT INTO " + DatabaseHelper.TABLE_ALERTS +
                                " (" + DatabaseHelper.COL_ALERT_ID + ", " +
                                DatabaseHelper.COL_USER_ID + ", " +
                                DatabaseHelper.COL_ALERT_TITLE + ", " +
                                DatabaseHelper.COL_ALERT_MESSAGE + ", " +
                                DatabaseHelper.COL_ALERT_TIME + ", " +
                                DatabaseHelper.COL_ALERT_READ + ", " +
                                DatabaseHelper.COL_LAST_SYNC +
                                ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                        new Object[]{
                                alert.getInt("alert_id"),
                                userId,
                                alert.getString("title"),
                                alert.getString("message"),
                                alert.getString("created_at"),
                                alert.getBoolean("is_read") ? 1 : 0,
                                System.currentTimeMillis()
                        });
            }
            Log.d(TAG, "Alerts saved: " + alerts.length());
        } catch (Exception e) {
            Log.e(TAG, "Error saving alerts: " + e.getMessage());
        }
    }

    public org.json.JSONArray getAlerts(int userId) {
        org.json.JSONArray result = new org.json.JSONArray();
        try {
            Cursor cursor = db.query(DatabaseHelper.TABLE_ALERTS,
                    null,
                    DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null,
                    DatabaseHelper.COL_ALERT_TIME + " DESC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject alert = new JSONObject();
                    alert.put("alert_id", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ALERT_ID)));
                    alert.put("title", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ALERT_TITLE)));
                    alert.put("message", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ALERT_MESSAGE)));
                    alert.put("created_at", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ALERT_TIME)));
                    alert.put("is_read", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ALERT_READ)) == 1);
                    result.put(alert);
                }
                cursor.close();
                Log.d(TAG, "Alerts retrieved: " + result.length());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving alerts: " + e.getMessage());
        }
        return result;
    }

    // ============ DONATIONS OPERATIONS ============

    public void saveDonations(int userId, String donationsJson) {
        try {
            org.json.JSONArray donations = new org.json.JSONArray(donationsJson);
            for (int i = 0; i < donations.length(); i++) {
                JSONObject donation = donations.getJSONObject(i);
                db.execSQL("INSERT OR REPLACE INTO " + DatabaseHelper.TABLE_DONATIONS +
                                " (" + DatabaseHelper.COL_DONATION_ID + ", " +
                                DatabaseHelper.COL_USER_ID + ", " +
                                DatabaseHelper.COL_AMOUNT + ", " +
                                DatabaseHelper.COL_DONATED_AT + ", " +
                                DatabaseHelper.COL_PAYMENT_STATUS + ", " +
                                DatabaseHelper.COL_LAST_SYNC +
                                ") VALUES (?, ?, ?, ?, ?, ?)",
                        new Object[]{
                                donation.getInt("monetary_donation_id"),
                                userId,
                                donation.getDouble("amount"),
                                donation.getString("donated_at"),
                                donation.getString("payment_status"),
                                System.currentTimeMillis()
                        });
            }
            Log.d(TAG, "Donations saved: " + donations.length());
        } catch (Exception e) {
            Log.e(TAG, "Error saving donations: " + e.getMessage());
        }
    }

    public org.json.JSONArray getDonations(int userId) {
        org.json.JSONArray result = new org.json.JSONArray();
        try {
            Cursor cursor = db.query(DatabaseHelper.TABLE_DONATIONS,
                    null,
                    DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null,
                    DatabaseHelper.COL_DONATED_AT + " DESC");

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject donation = new JSONObject();
                    donation.put("monetary_donation_id", cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DONATION_ID)));
                    donation.put("amount", cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_AMOUNT)));
                    donation.put("donated_at", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_DONATED_AT)));
                    donation.put("payment_status", cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PAYMENT_STATUS)));
                    result.put(donation);
                }
                cursor.close();
                Log.d(TAG, "Donations retrieved: " + result.length());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving donations: " + e.getMessage());
        }
        return result;
    }

    // ============ CACHE CHECK ============

    public boolean isCacheValid(int userId, long maxAgeMinutes) {
        try {
            Cursor cursor = db.query(DatabaseHelper.TABLE_USERS,
                    new String[]{DatabaseHelper.COL_LAST_SYNC},
                    DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                long lastSync = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_LAST_SYNC));
                long ageMinutes = (System.currentTimeMillis() - lastSync) / (1000 * 60);
                cursor.close();
                boolean valid = ageMinutes < maxAgeMinutes;
                Log.d(TAG, "Cache age: " + ageMinutes + " minutes. Valid: " + valid);
                return valid;
            }
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error checking cache: " + e.getMessage());
        }
        return false;
    }

    // ============ CLEAR ALL DATA ============

    public void clearAllData(int userId) {
        try {
            Log.d(TAG, "Clearing all data for userId: " + userId);

            db.delete(DatabaseHelper.TABLE_ALERTS, DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            Log.d(TAG, "Alerts cleared");

            db.delete(DatabaseHelper.TABLE_HOME_DATA, DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            Log.d(TAG, "Home data cleared");

            db.delete(DatabaseHelper.TABLE_LEADERBOARD, DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            Log.d(TAG, "Leaderboard cleared");

            db.delete(DatabaseHelper.TABLE_DONATIONS, DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            Log.d(TAG, "Donations cleared");

            db.delete(DatabaseHelper.TABLE_USERS, DatabaseHelper.COL_USER_ID + " = ?",
                    new String[]{String.valueOf(userId)});
            Log.d(TAG, "Users cleared");

            Log.d(TAG, "All data cleared successfully for userId: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing data: " + e.getMessage());
        }
    }

    // ============ CLOSE DATABASE ============

    public void close() {
        try {
            if (db != null && db.isOpen()) {
                db.close();
            }
            Log.d(TAG, "Database closed");
        } catch (Exception e) {
            Log.e(TAG, "Error closing database: " + e.getMessage());
        }
    }
}