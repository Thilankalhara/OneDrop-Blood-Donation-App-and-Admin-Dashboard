package com.example.onedrop.Activity.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * TokenDebugHelper
 * ─────────────────────────────────────────────────────────────────────────────
 * TEMPORARY debug tool — call debugSession(context) from MainActivity.onCreate()
 * to print the full session state to Logcat.
 *
 * Search Logcat for "TOKEN_DEBUG" to find the output.
 *
 * DELETE this class once the login loop is fixed.
 *
 * Usage in MainActivity.onCreate():
 *   TokenDebugHelper.debugSession(this);
 */
public class TokenDebugHelper {

    private static final String TAG = "TOKEN_DEBUG";

    public static void debugSession(Context ctx) {
        // ── UserSession prefs ──────────────────────────────────────────────
        SharedPreferences session = ctx.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        String  token           = session.getString("jwt_token",       null);
        int     userId          = session.getInt("userId",             -1);
        boolean isAuthenticated = session.getBoolean("isAuthenticated", false);
        String  name            = session.getString("name",            "");
        String  email           = session.getString("email",           "");

        // ── OneDropPrefs ───────────────────────────────────────────────────
        SharedPreferences oneDropPrefs = ctx.getSharedPreferences("OneDropPrefs", Context.MODE_PRIVATE);
        boolean onboardingCompleted = oneDropPrefs.getBoolean("onboarding_completed", false);
        boolean remembered          = oneDropPrefs.getBoolean("remembered",           false);

        Log.d(TAG, "══════════════════════════════════════════");
        Log.d(TAG, "SESSION STATE in MainActivity.onCreate()");
        Log.d(TAG, "══════════════════════════════════════════");
        Log.d(TAG, "UserSession / jwt_token       = " + (token != null
                ? "EXISTS (length=" + token.length() + " prefix=" + token.substring(0, Math.min(30, token.length())) + "...)"
                : "NULL ← token is missing!"));
        Log.d(TAG, "UserSession / userId          = " + userId);
        Log.d(TAG, "UserSession / isAuthenticated = " + isAuthenticated);
        Log.d(TAG, "UserSession / name            = " + name);
        Log.d(TAG, "UserSession / email           = " + email);
        Log.d(TAG, "OneDropPrefs/ onboarding      = " + onboardingCompleted);
        Log.d(TAG, "OneDropPrefs/ remembered      = " + remembered);
        Log.d(TAG, "AuthManager.isLoggedIn()      = " + AuthManager.isLoggedIn(ctx));
        Log.d(TAG, "══════════════════════════════════════════");

        if (token == null) {
            Log.e(TAG, "❌ TOKEN IS NULL — saveSession() either didn't run or saved to wrong prefs file");
        } else if (!isAuthenticated) {
            Log.e(TAG, "❌ isAuthenticated=false — SignInActivity didn't set it after login");
        } else {
            Log.d(TAG, "✅ Token and auth flag both present — session looks correct");
        }
    }
}