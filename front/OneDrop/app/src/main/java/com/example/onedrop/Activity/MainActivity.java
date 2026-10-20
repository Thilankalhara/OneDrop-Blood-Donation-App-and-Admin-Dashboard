package com.example.onedrop.Activity;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.onedrop.Activity.Fragment.DonateFragment;
import com.example.onedrop.Activity.Fragment.HomeFragment;
import com.example.onedrop.Activity.Fragment.LeaderboardFragment;
import com.example.onedrop.Activity.Fragment.ProfileFragment;
import com.example.onedrop.Activity.Fragment.ScanFragment;
import com.example.onedrop.Activity.Fragment.SplashActivity;
import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.Activity.Utils.TokenDebugHelper;
import com.example.onedrop.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG              = "MainActivity";
    private static final int    ACTIVE_COLOR     = 0xFFE53935;
    private static final String CHANNEL_ID       = "onedrop_alerts";
    private static final String CHANNEL_NAME     = "OneDrop Alerts";
    private static final String PREF_LAST_ALERT  = "last_seen_alert_id";
    private static final long   POLL_INTERVAL_MS = 30_000;

    private BottomNavigationView bottomNavigation;
    private SharedPreferences    sharedPreferences;
    private FragmentManager      fragmentManager;

    private volatile boolean sessionInvalidated = false;

    private final Handler         pollHandler   = new Handler(Looper.getMainLooper());
    private final ExecutorService pollExecutor  = Executors.newSingleThreadExecutor();
    private final ExecutorService checkExecutor = Executors.newSingleThreadExecutor();
    private Runnable              pollRunnable;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final ActivityResultLauncher<String> notificationPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        Log.d(TAG, "Notification permission: " + isGranted);
                        createNotificationChannel();
                        startPolling();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);

        // ✅ CRITICAL: Check if we should redirect to splash screen
        // This handles the case where user opens from recent apps
        if (!shouldShowMainActivity()) {
            redirectToSplash();
            return;
        }

        setContentView(R.layout.activity_main);
        TokenDebugHelper.debugSession(this);

        bottomNavigation = findViewById(R.id.bottomNavigation);
        fragmentManager  = getSupportFragmentManager();

        bottomNavigation.setItemRippleColor(null);
        bottomNavigation.setItemActiveIndicatorEnabled(false);

        bottomNavigation.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        bottomNavigation.getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);
                        setupItemHighlights(R.id.nav_home);
                    }
                });

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        bottomNavigation.setOnItemSelectedListener(item -> {
            if (!isConnected()) {
                Log.d(TAG, "Nav blocked — no internet");
                return false;
            }
            Fragment f  = null;
            int      id = item.getItemId();
            if      (id == R.id.nav_home)        f = new HomeFragment();
            else if (id == R.id.nav_leaderboard) f = new LeaderboardFragment();
            else if (id == R.id.nav_scan)        f = new ScanFragment();
            else if (id == R.id.nav_donate)      f = new DonateFragment();
            else if (id == R.id.nav_profile)     f = new ProfileFragment();
            if (f != null) loadFragment(f);
            setupItemHighlights(id);
            return true;
        });

        setupNetworkCallback();
        updateNavBarState(isConnected());
        requestNotificationPermission();

        validateSession();
    }

    // ✅ NEW: Check if MainActivity should be shown
    private boolean shouldShowMainActivity() {
        // ✅ Check BOTH JWT token AND isAuthenticated flag
        boolean hasToken = AuthManager.isLoggedIn(this);
        boolean isAuthenticated = sharedPreferences.getBoolean("isAuthenticated", false);

        Log.d(TAG, "shouldShowMainActivity: hasToken=" + hasToken + ", isAuthenticated=" + isAuthenticated);

        return hasToken && isAuthenticated;
    }

    // ✅ NEW: Redirect to splash screen
    private void redirectToSplash() {
        Log.d(TAG, "Redirecting to SplashActivity");
        startActivity(new Intent(this, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void validateSession() {
        int userId = sharedPreferences.getInt("userId", -1);
        if (userId == -1) { redirectToSignIn(); return; }

        checkExecutor.execute(() -> {
            try {
                String result = httpGet(Constants.SESSION_CHECK_ENDPOINT + "?userId=" + userId);
                Log.d(TAG, "Startup session check: " + result);
                if (result.trim().startsWith("<")) return;
                handleSessionResponse(result);
            } catch (Exception e) {
                Log.w(TAG, "Startup session check failed: " + e.getMessage());
            }
        });
    }

    private void checkSessionInBackground(int userId) {
        try {
            String result = httpGet(Constants.SESSION_CHECK_ENDPOINT + "?userId=" + userId);
            Log.d(TAG, "Poll session check: " + result);
            if (result.trim().startsWith("<")) return;
            handleSessionResponse(result);
        } catch (Exception e) {
            Log.w(TAG, "Poll session check failed: " + e.getMessage());
        }
    }

    private void handleSessionResponse(String result) {
        try {
            JsonObject json   = JsonParser.parseString(result).getAsJsonObject();
            boolean    valid  = json.has("valid") && json.get("valid").getAsBoolean();
            String     reason = json.has("reason") ? json.get("reason").getAsString() : "";

            if (!valid && !sessionInvalidated) {
                sessionInvalidated = true;
                Log.w(TAG, "Session invalid — reason: " + reason);

                String message;
                if ("user_not_found".equals(reason)) {
                    message = "Your account no longer exists.";
                } else if ("account_inactive".equals(reason)) {
                    message = "Your account has been deactivated. Contact support.";
                } else {
                    message = "Session expired. Please sign in again.";
                }

                final String finalMessage = message;
                runOnUiThread(() -> {
                    AuthManager.clearSession(this);
                    Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                    redirectToSignIn();
                });
            }
        } catch (Exception e) {
            Log.w(TAG, "handleSessionResponse error: " + e.getMessage());
        }
    }

    public void navigateToProfile() {
        bottomNavigation.setSelectedItemId(R.id.nav_profile);
        loadFragment(new ProfileFragment());
        setupItemHighlights(R.id.nav_profile);
    }

    public void lockNavBar() {
        runOnUiThread(() -> {
            if (bottomNavigation == null) return;
            bottomNavigation.animate().alpha(0.35f).setDuration(300).start();
            bottomNavigation.setEnabled(false);
            Menu menu = bottomNavigation.getMenu();
            for (int i = 0; i < menu.size(); i++) menu.getItem(i).setEnabled(false);
            Log.d(TAG, "Nav bar locked");
        });
    }

    public void unlockNavBar() {
        runOnUiThread(() -> {
            if (bottomNavigation == null) return;
            bottomNavigation.animate().alpha(1f).setDuration(300).start();
            bottomNavigation.setEnabled(true);
            Menu menu = bottomNavigation.getMenu();
            for (int i = 0; i < menu.size(); i++) menu.getItem(i).setEnabled(true);
            Log.d(TAG, "Nav bar unlocked");
        });
    }

    public boolean isConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    private void setupNetworkCallback() {
        connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Network available");
                    updateNavBarState(true);
                    Fragment current = fragmentManager.findFragmentById(R.id.fragmentContainer);
                    if (current instanceof HomeFragment)
                        ((HomeFragment) current).onNetworkRestored();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Network lost");
                    updateNavBarState(false);
                    Fragment current = fragmentManager.findFragmentById(R.id.fragmentContainer);
                    if (current instanceof HomeFragment)
                        ((HomeFragment) current).showNoInternet();
                });
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void updateNavBarState(boolean connected) {
        if (connected) unlockNavBar();
        else lockNavBar();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                createNotificationChannel();
                startPolling();
            } else {
                notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            createNotificationChannel();
            startPolling();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Blood donation alerts from OneDrop");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 250, 100, 250});
            channel.enableLights(true);
            channel.setLightColor(0xFFDC143C);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }

    private void startPolling() {
        int userId = sharedPreferences.getInt("userId", -1);
        if (userId == -1) return;
        pollRunnable = new Runnable() {
            @Override public void run() {
                pollForAlerts(userId);
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        pollHandler.post(pollRunnable);
        Log.d(TAG, "Polling started");
    }

    private void pollForAlerts(int userId) {
        pollExecutor.execute(() -> {

            checkSessionInBackground(userId);

            if (sessionInvalidated) return;

            try {
                String     result     = httpGet(Constants.HOME_DATA_ENDPOINT + "?userId=" + userId);
                JsonObject json       = JsonParser.parseString(result).getAsJsonObject();
                JsonArray  alertsJson = json.has("alerts") ? json.getAsJsonArray("alerts") : null;

                if (alertsJson == null || alertsJson.size() == 0) return;

                int    lastSeen      = sharedPreferences.getInt(PREF_LAST_ALERT, -1);
                int    newLastId     = lastSeen;
                int    newCount      = 0;
                String latestTitle   = "";
                String latestMessage = "";

                for (JsonElement el : alertsJson) {
                    JsonObject alert = el.getAsJsonObject();
                    int        id    = alert.has("alert_id") ? alert.get("alert_id").getAsInt() : -1;
                    if (id > lastSeen) {
                        newCount++;
                        if (id > newLastId) {
                            newLastId     = id;
                            latestTitle   = alert.has("title")   ? alert.get("title").getAsString()   : "New Alert";
                            latestMessage = alert.has("message") ? alert.get("message").getAsString() : "";
                        }
                    }
                }

                if (newCount > 0) {
                    String title = newCount > 1
                            ? "🩸 " + newCount + " New Alerts"
                            : "🩸 " + latestTitle;
                    String msg = newCount > 1
                            ? "You have " + newCount + " new blood donation alerts"
                            : latestMessage;
                    showNotification(title, msg);
                    sharedPreferences.edit().putInt(PREF_LAST_ALERT, newLastId).apply();
                }

            } catch (Exception e) {
                Log.e(TAG, "Poll error: " + e.getMessage(), e);
            }
        });
    }

    private void showNotification(String title, String message) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_blood_drop_notification)
                        .setColor(0xFFDC143C)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .setVibrate(new long[]{0, 250, 100, 250})
                        .setShowWhen(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void setupItemHighlights(int selectedItemId) {
        ViewGroup menuView = (ViewGroup) bottomNavigation.getChildAt(0);
        if (menuView == null) return;

        int[] itemIds = {
                R.id.nav_home, R.id.nav_leaderboard,
                R.id.nav_scan, R.id.nav_donate, R.id.nav_profile
        };

        float density      = getResources().getDisplayMetrics().density;
        int   navFrameSize = (int) (56 * density);

        for (int i = 0; i < menuView.getChildCount(); i++) {
            View    navItem  = menuView.getChildAt(i);
            boolean isActive = (i < itemIds.length) && (itemIds[i] == selectedItemId);

            if (isActive) {
                int hInset = Math.max(0, (navItem.getWidth()  - navFrameSize) / 2);
                int vInset = Math.max(0, (navItem.getHeight() - navFrameSize) / 2);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(ACTIVE_COLOR);
                navItem.setBackground(new InsetDrawable(
                        circle, hInset, vInset, hInset, vInset));
            } else {
                navItem.setBackground(null);
            }
        }
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction tx = fragmentManager.beginTransaction();
        tx.setCustomAnimations(
                android.R.anim.fade_in,  android.R.anim.fade_out,
                android.R.anim.fade_in,  android.R.anim.fade_out);
        tx.replace(R.id.fragmentContainer, fragment);
        tx.commit();
    }

    private void redirectToSignIn() {
        startActivity(new Intent(this, SignInActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    public String getCurrentUserEmail() {
        return sharedPreferences.getString("email", "");
    }

    public int getCurrentUserId() {
        return sharedPreferences.getInt("userId", -1);
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn =
                (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        AuthManager.attachToken(this, conn);

        conn.connect();
        int code = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code < 400 ? conn.getInputStream() : conn.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();
        return sb.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); }
            catch (Exception e) { Log.e(TAG, "Unregister error: " + e.getMessage()); }
        }
        if (pollRunnable != null) pollHandler.removeCallbacks(pollRunnable);
        pollExecutor.shutdown();
        checkExecutor.shutdown();
    }
}