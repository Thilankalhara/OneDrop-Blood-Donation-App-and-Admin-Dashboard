package com.example.onedrop.Activity.Fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.example.onedrop.Activity.MainActivity;
import com.example.onedrop.Activity.OnboardingActivity;
import com.example.onedrop.Activity.SignInActivity;
import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.R;

public class SplashActivity extends AppCompatActivity {

    private static final String TAG = "SplashActivity";
    private boolean navigationStarted = false;
    private Handler navigationHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        hideSystemUI();

        LottieAnimationView lottieView = findViewById(R.id.lottieSplash);
        if (lottieView != null) {
            lottieView.setImageAssetsFolder("images/");
        }

        navigationHandler = new Handler(Looper.getMainLooper());
        navigationHandler.postDelayed(this::navigate, 2200);
    }

    private void navigate() {
        if (navigationStarted) {
            Log.d(TAG, "Navigation already started, skipping");
            return;
        }

        navigationStarted = true;
        Log.d(TAG, "Starting navigation");

        SharedPreferences oneDropPrefs =
                getSharedPreferences("OneDropPrefs", MODE_PRIVATE);
        SharedPreferences userSession =
                getSharedPreferences("UserSession", MODE_PRIVATE);

        boolean onboardingCompleted = oneDropPrefs.getBoolean("onboarding_completed", false);
        boolean hasToken = AuthManager.isLoggedIn(this);
        boolean isAuthenticated = userSession.getBoolean("isAuthenticated", false);
        boolean isLoggedIn = hasToken && isAuthenticated;

        Log.d(TAG, "onboardingCompleted=" + onboardingCompleted);
        Log.d(TAG, "hasToken=" + hasToken);
        Log.d(TAG, "isAuthenticated=" + isAuthenticated);
        Log.d(TAG, "isLoggedIn=" + isLoggedIn);

        Intent intent;
        if (!onboardingCompleted) {
            Log.d(TAG, "→ Navigating to Onboarding");
            intent = new Intent(this, OnboardingActivity.class);
        } else if (isLoggedIn) {
            Log.d(TAG, "→ Navigating to MainActivity");
            intent = new Intent(this, MainActivity.class);
        } else {
            Log.d(TAG, "→ Navigating to SignIn");
            intent = new Intent(this, SignInActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        // ✅ IMPORTANT: Finish splash screen after navigation
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent called");
        setIntent(intent);

        // ✅ Reset only if we haven't already navigated
        if (!navigationStarted) {
            navigationStarted = false;

            if (navigationHandler != null) {
                navigationHandler.removeCallbacksAndMessages(null);
            }

            navigationHandler = new Handler(Looper.getMainLooper());
            navigationHandler.postDelayed(this::navigate, 2200);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called, navigationStarted=" + navigationStarted);
        hideSystemUI();

        // ✅ IMPORTANT: If we've already navigated, finish immediately
        if (navigationStarted) {
            Log.d(TAG, "Already navigated, finishing");
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        if (navigationHandler != null) {
            navigationHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        if (navigationHandler != null) {
            navigationHandler.removeCallbacksAndMessages(null);
            navigationHandler = null;
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }
}