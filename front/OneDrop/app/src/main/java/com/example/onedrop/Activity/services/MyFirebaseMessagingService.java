package com.example.onedrop.Activity.services;


import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles incoming FCM messages and token refresh.
 * Since the admin inserts alerts directly into the DB,
 * we use a polling WorkManager job to check for new alerts.
 * This service handles token updates so the backend always
 * has the latest FCM token for future push support.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        // Save token locally — AlertPollingWorker will sync it to server
        getSharedPreferences("UserSession", MODE_PRIVATE)
                .edit()
                .putString("fcm_token", token)
                .apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // Handle direct FCM push if you later switch to server-push model
        Log.d(TAG, "FCM message received: " + remoteMessage.getData());
    }
}