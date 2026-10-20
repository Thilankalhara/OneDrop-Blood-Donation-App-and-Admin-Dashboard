package com.example.onedrop.Activity.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.onedrop.Activity.MainActivity;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AlertPollingWorker extends Worker {

    private static final String TAG             = "AlertPollingWorker";
    private static final String CHANNEL_ID      = "onedrop_alerts";
    private static final String CHANNEL_NAME    = "OneDrop Alerts";
    private static final String PREF_LAST_ALERT = "last_seen_alert_id";

    public AlertPollingWorker(@NonNull Context context,
                              @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context           context = getApplicationContext();
        SharedPreferences prefs   = context.getSharedPreferences(
                "UserSession", Context.MODE_PRIVATE);

        int userId = prefs.getInt("userId", -1);
        if (userId == -1) {
            Log.d(TAG, "No userId in session — skipping poll");
            return Result.success();
        }

        int lastSeenAlertId = prefs.getInt(PREF_LAST_ALERT, -1);
        Log.d(TAG, "Polling — userId=" + userId
                + " | lastSeenAlertId=" + lastSeenAlertId);

        try {
            String result = httpGet(
                    Constants.HOME_DATA_ENDPOINT + "?userId=" + userId);

            JSONObject json       = new JSONObject(result);
            JSONArray  alertsJson = json.optJSONArray("alerts");

            if (alertsJson == null || alertsJson.length() == 0) {
                Log.d(TAG, "No alerts in response");
                return Result.success();
            }

            int    newLastAlertId = lastSeenAlertId;
            int    newAlertCount  = 0;
            String latestTitle    = "";
            String latestMessage  = "";

            for (int i = 0; i < alertsJson.length(); i++) {
                JSONObject alert   = alertsJson.getJSONObject(i);
                int        alertId = alert.optInt("alert_id", -1);

                if (alertId > lastSeenAlertId) {
                    newAlertCount++;
                    if (alertId > newLastAlertId) {
                        newLastAlertId = alertId;
                        latestTitle    = alert.optString("title",   "New Alert");
                        latestMessage  = alert.optString("message", "");
                    }
                }
            }

            if (newAlertCount > 0) {
                Log.d(TAG, newAlertCount + " new alert(s) — showing notification");

                String notifTitle = newAlertCount > 1
                        ? "🩸 " + newAlertCount + " New Alerts"
                        : "🩸 " + latestTitle;

                String notifMessage = newAlertCount > 1
                        ? "You have " + newAlertCount + " new alerts from OneDrop"
                        : latestMessage;

                showNotification(context, notifTitle, notifMessage);

                // Save new last seen alert id
                prefs.edit().putInt(PREF_LAST_ALERT, newLastAlertId).apply();
            } else {
                Log.d(TAG, "No new alerts since last poll");
            }

            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Polling error: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    // ── Build and show the notification ───────────────────────────────────
    private void showNotification(Context context, String title, String message) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        // ── Create channel for Android 8+ ──────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Blood donation alerts from OneDrop");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 100, 250});
            channel.enableLights(true);
            channel.setLightColor(0xFFDC143C);
            manager.createNotificationChannel(channel);
        }

        // ── Tap → open MainActivity ────────────────────────────────────
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // ── Build notification ─────────────────────────────────────────
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)

                        // ✅ Blood drop icon
                        .setSmallIcon(R.drawable.ic_blood_drop_notification)

                        // Red tint on the icon
                        .setColor(0xFFDC143C)
                        .setColorized(true)

                        .setContentTitle(title)
                        .setContentText(message)

                        // Expand full message on pull-down
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(message)
                                .setBigContentTitle(title)
                                .setSummaryText("OneDrop"))

                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)

                        // Vibrate on arrival
                        .setVibrate(new long[]{0, 250, 100, 250})

                        // Show timestamp
                        .setShowWhen(true)
                        .setWhen(System.currentTimeMillis());

        // Use unique ID per notification so multiple alerts stack
        int notificationId = (int) System.currentTimeMillis();
        manager.notify(notificationId, builder.build());

        Log.d(TAG, "Notification shown — id=" + notificationId + " title=" + title);
    }

    // ── HTTP GET ───────────────────────────────────────────────────────────
    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.connect();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        return sb.toString();
    }
}