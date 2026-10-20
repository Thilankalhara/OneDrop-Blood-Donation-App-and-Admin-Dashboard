package com.example.onedrop.Activity.Fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.onedrop.Activity.Adapter.AlertAdapter;
import com.example.onedrop.Activity.Database.UserDataManager;
import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.hdodenhof.circleimageview.CircleImageView;

public class HomeFragment extends Fragment implements SensorEventListener {

    private static final String TAG              = "HomeFragment";
    private static final int    AUTO_SLIDE_DELAY = 4000;
    private static final float  SHAKE_THRESHOLD_G = 2.7f;
    private static final long   SHAKE_DEBOUNCE_MS = 300;
    private static final long   SHAKE_WINDOW_MS   = 3000;
    private static final int    REQUIRED_SHAKES   = 3;
    private static final long   CACHE_VALIDITY_MINUTES = 30;

    private SwipeRefreshLayout swipeRefreshLayout;
    private ViewPager2         quoteViewPager;
    private LinearLayout       quoteDots;
    private Handler            autoSlideHandler;
    private Runnable           autoSlideRunnable;

    private TextView        notificationBadge, donationCountTv, livesTv;
    private TextView        bloodTypeTv, activeTv, userNameText, greetingText, greetingEmoji;
    private RecyclerView    messagesRecyclerView;
    private TextView        noMessagesText;
    private CircleImageView userAvatar;

    private LinearLayout noInternetOverlay;
    private TextView     noInternetIcon, noInternetTitle, noInternetSubtitle, retryButton;
    private FrameLayout  shakeOverlay;
    private TextView     shakeBloodDrop;

    private SensorManager sensorManager;
    private Sensor        accelerometer;

    private int     shakeCount     = 0;
    private long    firstShakeTime = 0;
    private long    lastShakeTime  = 0;
    private boolean shakeAnimating = false;

    private final List<AlertItem> alertList   = new ArrayList<>();
    private AlertAdapter          alertAdapter;

    private final ExecutorService executor    = Executors.newFixedThreadPool(2);
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private UserDataManager userDataManager;


    private boolean cacheLoadedThisSession = false;
    private int currentUserId = -1;

    private final String[] quotes = {
            "\"The gift of blood is the gift of life. There is no substitute for human blood.\"",
            "\"Donate blood. You may save the life of someone you know.\"",
            "\"Blood donors are everyday heroes who give the gift of life.\"",
            "\"Every drop of blood donated is a step toward saving a life.\"",
            "\"Be a hero. Donate blood and save up to 3 lives with one donation.\""
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefreshLayout   = view.findViewById(R.id.swipeRefreshLayout);
        notificationBadge    = view.findViewById(R.id.notificationBadge);
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView);
        noMessagesText       = view.findViewById(R.id.noMessagesText);
        donationCountTv      = view.findViewById(R.id.donationCountText);
        livesTv              = view.findViewById(R.id.livesText);
        bloodTypeTv          = view.findViewById(R.id.bloodTypeValue);
        activeTv             = view.findViewById(R.id.activeValue);
        userNameText         = view.findViewById(R.id.userNameText);
        greetingText         = view.findViewById(R.id.greetingText);
        greetingEmoji        = view.findViewById(R.id.greetingEmoji);
        userAvatar           = view.findViewById(R.id.userAvatar);
        noInternetOverlay    = view.findViewById(R.id.noInternetOverlay);
        noInternetIcon       = view.findViewById(R.id.noInternetIcon);
        noInternetTitle      = view.findViewById(R.id.noInternetTitle);
        noInternetSubtitle   = view.findViewById(R.id.noInternetSubtitle);
        retryButton          = view.findViewById(R.id.retryButton);
        shakeOverlay         = view.findViewById(R.id.shakeOverlay);
        shakeBloodDrop       = view.findViewById(R.id.shakeBloodDrop);

        userDataManager = new UserDataManager(requireContext());
        currentUserId = AuthManager.getUserId(requireContext());

        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        alertAdapter = new AlertAdapter(alertList, new AlertAdapter.OnAlertDeletedListener() {
            @Override public void onAlertDeleted(int position) { updateEmptyState(); }
            @Override public void onDeleteFailed(int position)  {}
        });
        messagesRecyclerView.setAdapter(alertAdapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_ID) alertAdapter.deleteItem(pos, requireContext());
            }
            @Override public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, float dX, float dY, int actionState, boolean isActive) {
                View card = vh.itemView.findViewById(R.id.alertCard);
                if (card != null) card.setTranslationX(dX);
                else super.onChildDraw(c, rv, vh, dX, dY, actionState, isActive);
            }
        }).attachToRecyclerView(messagesRecyclerView);

        swipeRefreshLayout.setColorSchemeColors(Color.parseColor("#DC143C"), Color.parseColor("#FF6B6B"), Color.parseColor("#B71C1C"));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(Color.WHITE);
        swipeRefreshLayout.setOnRefreshListener(this::refreshAllData);
        retryButton.setOnClickListener(v -> { animateButtonPress(retryButton); refreshAllData(); });

        setupGreeting();
        loadSessionData();
        setupQuoteSlider();
        loadAvatarFromSession();

        // IMPORTANT: Load cache first if not already loaded this session
        if (!cacheLoadedThisSession && currentUserId != -1) {
            cacheLoadedThisSession = true;
            loadHomeDataFromCache(currentUserId);

            // Then fetch if internet is available (background)
            if (isConnected()) {
                mainHandler.postDelayed(this::fetchHomeData, 500); // Small delay to avoid UI jank
            } else {
                showNoInternet(false);
            }
        } else if (currentUserId != -1) {
            // Already loaded cache this session, just fetch if online
            if (isConnected()) {
                hideNoInternet();
                fetchHomeData();
            } else {
                showNoInternet(false);
            }
        } else {
            noMessagesText.setVisibility(View.VISIBLE);
        }
    }

    private void loadHomeDataFromCache(int userId) {
        executor.execute(() -> {
            try {
                JSONObject homeData = userDataManager.getHomeData(userId);
                JSONArray alerts = userDataManager.getAlerts(userId);

                if (homeData != null) {
                    int donations = homeData.optInt("donation_count", 0);
                    int lives = homeData.optInt("lives_saved", 0);

                    mainHandler.post(() -> {
                        if (getView() == null) return;
                        donationCountTv.setText(String.valueOf(donations));
                        livesTv.setText("You've helped save up to " + lives + " lives");

                        alertList.clear();
                        for (int i = 0; i < alerts.length(); i++) {
                            try {
                                JSONObject alertObj = alerts.getJSONObject(i);
                                AlertItem item = new AlertItem();
                                item.alertId = alertObj.optInt("alert_id");
                                item.title = alertObj.optString("title", "Alert");
                                item.message = alertObj.optString("message", "");
                                item.time = alertObj.optString("created_at", "");
                                item.isRead = alertObj.optBoolean("is_read", false);
                                alertList.add(item);
                            } catch (Exception e) {
                                Log.e(TAG, "Alert parse error: " + e.getMessage());
                            }
                        }
                        alertAdapter.notifyDataSetChanged();
                        updateEmptyState();
                        updateNotificationBadge(alerts.length());

                        Log.d(TAG, "✅ UI loaded from cache instantly");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Cache load error: " + e.getMessage());
            }
        });
    }

    private void loadAvatarFromSession() {
        String base64 = requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                .getString("profile_image", "");
        if (!base64.isEmpty()) decodeAndSetAvatar(base64);
    }

    private void decodeAndSetAvatar(String base64) {
        executor.execute(() -> {
            try {
                byte[] bytes  = Base64.decode(base64.replaceAll("\\s+", ""), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                mainHandler.post(() -> {
                    if (getView() == null || bitmap == null || userAvatar == null) return;
                    userAvatar.setImageBitmap(bitmap);
                });
            } catch (Exception e) { Log.e(TAG, "decodeAndSetAvatar: " + e.getMessage()); }
        });
    }

    public void onNetworkRestored() {
        if (currentUserId != -1) {
            if (userDataManager.isCacheValid(currentUserId, CACHE_VALIDITY_MINUTES)) {
                Log.d(TAG, "Network restored: Cache still valid");
                return;
            } else {
                Log.d(TAG, "Network restored: Fetching fresh data");
                fetchHomeData();
            }
        }
        hideNoInternet();
        unlockNavBar();
    }

    public void showNoInternet(boolean isServerError) {
        if (getView() == null) return;
        if (noInternetOverlay.getVisibility() == View.VISIBLE) { updateOverlayText(isServerError); return; }
        swipeRefreshLayout.setRefreshing(false);
        updateOverlayText(isServerError);
        noInternetOverlay.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f); fadeIn.setDuration(300);
        noInternetOverlay.startAnimation(fadeIn);
        swipeRefreshLayout.animate().alpha(0.25f).setDuration(300).start();
        startIconBounce(); lockNavBar();
    }
    public void showNoInternet() { showNoInternet(false); }

    private void updateOverlayText(boolean isServerError) {
        if (isServerError) { noInternetIcon.setText("⚠️"); noInternetTitle.setText("Server Unavailable"); noInternetSubtitle.setText("We're having trouble reaching\nthe server. Please try again."); }
        else { noInternetIcon.setText("📡"); noInternetTitle.setText("No Connection"); noInternetSubtitle.setText("Check your internet connection\nand try again."); }
    }

    public void hideNoInternet() {
        if (getView() == null || noInternetOverlay.getVisibility() == View.GONE) return;
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f); fadeOut.setDuration(250);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) { noInternetOverlay.setVisibility(View.GONE); noInternetIcon.clearAnimation(); }
        });
        noInternetOverlay.startAnimation(fadeOut);
        swipeRefreshLayout.animate().alpha(1f).setDuration(250).start();
    }

    private void lockNavBar()   { if (getActivity() instanceof com.example.onedrop.Activity.MainActivity) ((com.example.onedrop.Activity.MainActivity) getActivity()).lockNavBar(); }
    private void unlockNavBar() { if (getActivity() instanceof com.example.onedrop.Activity.MainActivity) ((com.example.onedrop.Activity.MainActivity) getActivity()).unlockNavBar(); }

    private void startIconBounce() {
        ScaleAnimation bounce = new ScaleAnimation(1f, 1.15f, 1f, 1.15f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        bounce.setDuration(700); bounce.setRepeatCount(Animation.INFINITE); bounce.setRepeatMode(Animation.REVERSE);
        noInternetIcon.startAnimation(bounce);
    }

    private void animateButtonPress(View v) {
        ScaleAnimation scale = new ScaleAnimation(1f, 0.95f, 1f, 0.95f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(100); scale.setRepeatCount(1); scale.setRepeatMode(Animation.REVERSE);
        v.startAnimation(scale);
    }

    private void refreshAllData() {
        if (!isConnected()) { swipeRefreshLayout.setRefreshing(false); showNoInternet(false); return; }
        hideNoInternet(); unlockNavBar(); setupGreeting(); loadSessionData(); loadAvatarFromSession();
        if (currentUserId != -1) fetchHomeData();
        else swipeRefreshLayout.setRefreshing(false);
    }

    @Override public void onResume() {
        super.onResume();
        loadAvatarFromSession();
        if (sensorManager != null && accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override public void onPause() { super.onPause(); if (sensorManager != null) sensorManager.unregisterListener(this); }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || shakeAnimating) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        float gForce = (float) Math.sqrt(x*x + y*y + z*z) / SensorManager.GRAVITY_EARTH;
        if (gForce > SHAKE_THRESHOLD_G) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime < SHAKE_DEBOUNCE_MS) return;
            lastShakeTime = now;
            if (firstShakeTime == 0 || now - firstShakeTime > SHAKE_WINDOW_MS) { firstShakeTime = now; shakeCount = 1; }
            else { shakeCount++; if (shakeCount >= REQUIRED_SHAKES) { shakeCount = 0; firstShakeTime = 0; lastShakeTime = 0; mainHandler.post(this::onThreeShakesDetected); } }
        }
    }

    private void onThreeShakesDetected() {
        if (shakeAnimating || getView() == null) return;
        shakeAnimating = true; vibrate(); showBloodDropAnimation();
        mainHandler.postDelayed(this::hideBloodDropAnimation, 2500);
    }

    private void vibrate() {
        Vibrator v = (Vibrator) requireActivity().getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            v.vibrate(VibrationEffect.createWaveform(new long[]{0,80,60,80,60,100}, new int[]{0,180,0,200,0,255}, -1));
        else v.vibrate(new long[]{0,80,60,80,60,100}, -1);
    }

    private void showBloodDropAnimation() {
        shakeOverlay.setVisibility(View.VISIBLE); shakeOverlay.setAlpha(0f);
        shakeOverlay.animate().alpha(1f).setDuration(200).start();
        shakeBloodDrop.setScaleX(0f); shakeBloodDrop.setScaleY(0f);
        shakeBloodDrop.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.5f))
                .withEndAction(() -> {
                    ScaleAnimation pulse = new ScaleAnimation(1f,1.1f,1f,1.1f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f);
                    pulse.setDuration(500); pulse.setRepeatCount(Animation.INFINITE); pulse.setRepeatMode(Animation.REVERSE);
                    shakeBloodDrop.startAnimation(pulse);
                }).start();
    }

    private void hideBloodDropAnimation() {
        if (getView() == null) return;
        shakeBloodDrop.clearAnimation();
        shakeOverlay.animate().alpha(0f).setDuration(300).withEndAction(() -> { shakeOverlay.setVisibility(View.GONE); shakeAnimating = false; }).start();
    }

    private void loadSessionData() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        String name = prefs.getString("name", ""), bloodType = prefs.getString("blood_type", "?"), createdAt = prefs.getString("created_at", "");
        if (!name.isEmpty()) userNameText.setText(capitalise(name));
        bloodTypeTv.setText(bloodType.isEmpty() ? "?" : bloodType);
        activeTv.setText(calcActiveDuration(createdAt));
    }

    private void setupGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting, emoji;
        if      (hour >= 5  && hour < 12) { greeting = "GOOD MORNING";   emoji = "☀️"; }
        else if (hour >= 12 && hour < 17) { greeting = "GOOD AFTERNOON"; emoji = "🌤️"; }
        else if (hour >= 17 && hour < 21) { greeting = "GOOD EVENING";   emoji = "🌆"; }
        else                              { greeting = "GOOD NIGHT";      emoji = "🌙"; }
        greetingText.setText(greeting); greetingEmoji.setText(" " + emoji);
    }

    private void fetchHomeData() {
        executor.execute(() -> {
            try {
                System.out.println("════════════════════════════════════════════════════════════");
                System.out.println("📱 HomeFragment.fetchHomeData()");
                System.out.println("════════════════════════════════════════════════════════════");

                String token = AuthManager.getToken(requireContext());
                System.out.println("🔐 JWT Token: " + (token != null ? token.substring(0, Math.min(50, token.length())) + "..." : "NULL"));

                if (token == null || token.isEmpty()) {
                    System.out.println("❌ ERROR: No JWT token!");
                    mainHandler.post(() -> {
                        if (getView() == null) return;
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(requireContext(), "Session lost. Please login again.", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                System.out.println("📡 Calling: " + Constants.HOME_DATA_ENDPOINT);
                String result = httpGet(Constants.HOME_DATA_ENDPOINT);

                System.out.println("📥 Response length: " + result.length());

                if (result.trim().startsWith("<!")) {
                    System.out.println("❌ Server returned HTML error");
                    mainHandler.post(() -> {
                        if (getView() == null) return;
                        swipeRefreshLayout.setRefreshing(false);
                        showNoInternet(true);
                    });
                    return;
                }

                if (result.trim().isEmpty()) {
                    System.out.println("❌ Empty response");
                    mainHandler.post(() -> {
                        if (getView() == null) return;
                        swipeRefreshLayout.setRefreshing(false);
                        showNoInternet(true);
                    });
                    return;
                }

                JSONObject json = new JSONObject(result);
                int donations = json.optInt("donation_count", 0);
                int lives = json.optInt("lives_saved", 0);
                int alertCnt = json.optInt("alert_count", 0);
                JSONArray alertsJson = json.optJSONArray("alerts");
                String profileImage = json.optString("profile_image", "");

                System.out.println("✅ Parsed successfully:");
                System.out.println("   📊 donations: " + donations);
                System.out.println("   ❤️  lives: " + lives);
                System.out.println("   🔔 alerts: " + alertCnt);

                // ✅ SAVE TO SQLITE
                userDataManager.saveHomeData(currentUserId, donations, lives);
                if (alertsJson != null) {
                    userDataManager.saveAlerts(currentUserId, alertsJson.toString());
                }

                List<AlertItem> fresh = new ArrayList<>();
                if (alertsJson != null) {
                    for (int i = 0; i < alertsJson.length(); i++) {
                        JSONObject obj = alertsJson.getJSONObject(i);
                        AlertItem item = new AlertItem();
                        item.alertId = obj.optInt("alert_id");
                        item.title = obj.optString("title", "Alert");
                        item.message = obj.optString("message", "");
                        item.time = obj.optString("created_at", "");
                        item.isRead = obj.optBoolean("is_read", false);
                        fresh.add(item);
                    }
                }

                if (!profileImage.isEmpty()) {
                    requireActivity().getSharedPreferences("UserSession", Context.MODE_PRIVATE)
                            .edit().putString("profile_image", profileImage).apply();
                }

                final String fProfileImage = profileImage;
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    swipeRefreshLayout.setRefreshing(false);
                    donationCountTv.setText(String.valueOf(donations));
                    livesTv.setText("You've helped save up to " + lives + " lives");
                    updateNotificationBadge(alertCnt);
                    alertList.clear();
                    alertList.addAll(fresh);
                    alertAdapter.notifyDataSetChanged();
                    updateEmptyState();
                    hideNoInternet();
                    unlockNavBar();
                    if (!fProfileImage.isEmpty()) decodeAndSetAvatar(fProfileImage);

                    System.out.println("✅ UI updated successfully");
                });

            } catch (Exception e) {
                System.out.println("❌ fetchHomeData error: " + e.getMessage());
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (getView() == null) return;
                    swipeRefreshLayout.setRefreshing(false);
                    showNoInternet(!isConnected() ? false : true);
                    updateEmptyState();
                });
            }

            System.out.println("════════════════════════════════════════════════════════════");
        });
    }

    private String httpGet(String urlStr) throws Exception {
        System.out.println("🔗 httpGet: " + urlStr);

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        AuthManager.attachToken(requireContext(), conn);

        conn.connect();
        int code = conn.getResponseCode();
        System.out.println("📊 Response code: " + code);

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code < 400 ? conn.getInputStream() : conn.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        conn.disconnect();

        return sb.toString();
    }

    private void updateEmptyState() {
        if (alertList.isEmpty()) { noMessagesText.setVisibility(View.VISIBLE); messagesRecyclerView.setVisibility(View.GONE); }
        else { noMessagesText.setVisibility(View.GONE); messagesRecyclerView.setVisibility(View.VISIBLE); }
    }

    private void updateNotificationBadge(int count) {
        if (notificationBadge == null) return;
        if (count > 0) { notificationBadge.setVisibility(View.VISIBLE); notificationBadge.setText(count > 99 ? "99+" : String.valueOf(count)); }
        else notificationBadge.setVisibility(View.GONE);
    }

    private void setupQuoteSlider() {
        quoteViewPager = requireView().findViewById(R.id.quoteViewPager);
        quoteDots      = requireView().findViewById(R.id.quoteDots);
        quoteViewPager.setAdapter(new QuoteAdapter(quotes));
        buildDots(0);
        quoteViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { buildDots(position); resetAutoSlide(); }
        });
        startAutoSlide();
    }

    private void buildDots(int activeIndex) {
        if (getContext() == null) return;
        quoteDots.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < quotes.length; i++) {
            View dot = new View(getContext());
            int  size = (int) ((i == activeIndex ? 8 : 6) * density);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            p.setMargins((int)(4*density),0,(int)(4*density),0); dot.setLayoutParams(p);
            GradientDrawable shape = new GradientDrawable(); shape.setShape(GradientDrawable.OVAL);
            shape.setColor(i == activeIndex ? Color.parseColor("#E53935") : Color.parseColor("#DDDDDD"));
            dot.setBackground(shape); quoteDots.addView(dot);
        }
    }

    private void startAutoSlide() {
        autoSlideHandler  = new Handler(Looper.getMainLooper());
        autoSlideRunnable = new Runnable() {
            @Override public void run() {
                if (quoteViewPager == null || getContext() == null) return;
                quoteViewPager.setCurrentItem((quoteViewPager.getCurrentItem()+1) % quotes.length, true);
                autoSlideHandler.postDelayed(this, AUTO_SLIDE_DELAY);
            }
        };
        autoSlideHandler.postDelayed(autoSlideRunnable, AUTO_SLIDE_DELAY);
    }

    private void resetAutoSlide() {
        if (autoSlideHandler != null && autoSlideRunnable != null) {
            autoSlideHandler.removeCallbacks(autoSlideRunnable);
            autoSlideHandler.postDelayed(autoSlideRunnable, AUTO_SLIDE_DELAY);
        }
    }

    private boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    private String capitalise(String s) { if (s==null||s.isEmpty()) return s; return Character.toUpperCase(s.charAt(0))+s.substring(1); }
    private String calcActiveDuration(String createdAt) {
        if (createdAt==null||createdAt.isEmpty()) return "New";
        try {
            String[] parts = createdAt.split(" ")[0].split("-");
            Calendar reg = Calendar.getInstance();
            reg.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])-1, Integer.parseInt(parts[2]));
            Calendar now = Calendar.getInstance();
            int totalMonths = (now.get(Calendar.YEAR)-reg.get(Calendar.YEAR))*12+(now.get(Calendar.MONTH)-reg.get(Calendar.MONTH));
            if (totalMonths<1) return "New"; if (totalMonths<12) return totalMonths+"mo";
            int y=totalMonths/12, m=totalMonths%12; return m>0 ? y+"yr "+m+"mo" : y+"yr";
        } catch (Exception e) { return "?"; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (autoSlideHandler!=null&&autoSlideRunnable!=null) autoSlideHandler.removeCallbacks(autoSlideRunnable);
        executor.shutdown();
        if (userDataManager != null) userDataManager.close();
    }

    public static class AlertItem {
        public int alertId; public String title, message, time; public boolean isRead;
    }
}