package com.example.onedrop.Activity.Utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.net.HttpURLConnection;

/**
 * AuthManager
 * ─────────────────────────────────────────────────────────────────────────────
 * Handles saving + reading the JWT token on the Android side.
 *
 * After login:
 *   AuthManager.saveSession(context, token, userId, name, email);
 *
 * Before every HTTP call:
 *   AuthManager.attachToken(context, conn);
 *
 * On logout:
 *   AuthManager.clearSession(context);
 *
 * On 401 response from server:
 *   AuthManager.handle401(context);
 */
public class AuthManager {

    private static final String TAG         = "AuthManager";
    private static final String PREFS       = "UserSession";
    private static final String KEY_TOKEN   = "jwt_token";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_NAME    = "name";
    private static final String KEY_EMAIL   = "email";

    // ── Save full session after login ──────────────────────────────────────
    public static void saveSession(Context ctx, String token,
                                   int userId, String name, String email) {
        Log.d(TAG, "Saving session — userId=" + userId
                + " tokenLength=" + (token != null ? token.length() : 0));
        System.out.println("💾 AuthManager.saveSession()");
        System.out.println("   🔐 Token length: " + (token != null ? token.length() : 0));
        System.out.println("   👤 userId: " + userId);
        System.out.println("   📧 email: " + email);

        prefs(ctx).edit()
                .putString(KEY_TOKEN,   token)
                .putInt(KEY_USER_ID,    userId)
                .putString(KEY_NAME,    name)
                .putString(KEY_EMAIL,   email)
                .apply();

        System.out.println("✅ Session saved to SharedPreferences");
    }

    // ── Getters ────────────────────────────────────────────────────────────
    public static String getToken(Context ctx) {
        String token = prefs(ctx).getString(KEY_TOKEN, null);
        System.out.println("📥 AuthManager.getToken(): " + (token != null ? token.substring(0, Math.min(50, token.length())) + "..." : "NULL"));
        return token;
    }

    public static int getUserId(Context ctx) {
        int userId = prefs(ctx).getInt(KEY_USER_ID, -1);
        System.out.println("📥 AuthManager.getUserId(): " + userId);
        return userId;
    }

    public static String getName(Context ctx) {
        return prefs(ctx).getString(KEY_NAME, "");
    }

    // ── Check if a token exists ────────────────────────────────────────────
    public static boolean isLoggedIn(Context ctx) {
        String token = getToken(ctx);
        boolean loggedIn = token != null && !token.isEmpty();
        System.out.println("🔐 AuthManager.isLoggedIn(): " + loggedIn);
        return loggedIn;
    }

    // ── Attach token to any HttpURLConnection ──────────────────────────────
    public static void attachToken(Context ctx, HttpURLConnection conn) {
        String token = getToken(ctx);
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
            System.out.println("✅ JWT attached to request");
            System.out.println("   🔐 Token: " + token.substring(0, Math.min(50, token.length())) + "...");
            System.out.println("   🔗 URL: " + conn.getURL().getPath());
        } else {
            System.out.println("❌ ERROR: attachToken() called but NO TOKEN");
            System.out.println("   🔗 URL: " + conn.getURL().getPath());
        }
    }

    /**
     * Called ONLY when a protected endpoint returns HTTP 401.
     * Clears the session and redirects to SignInActivity.
     *
     * ⚠️  DO NOT call this on network errors (IOException) or 5xx errors.
     *     Only call it when the server explicitly returns 401.
     */
    public static void handle401(Context ctx) {
        Log.w(TAG, "handle401: Server rejected token — clearing session and redirecting to sign-in");
        clearSession(ctx);
        try {
            Class<?> signInClass = Class.forName("com.example.onedrop.Activity.SignInActivity");
            Intent intent = new Intent(ctx, signInClass);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "handle401 navigation failed: " + e.getMessage());
        }
    }

    // ── Clear on logout ────────────────────────────────────────────────────
    public static void clearSession(Context ctx) {
        Log.d(TAG, "clearSession called");
        System.out.println("🗑️  AuthManager.clearSession()");
        prefs(ctx).edit().clear().apply();
        ctx.getSharedPreferences("OneDropPrefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("remembered", false)
                .apply();
        System.out.println("✅ Session cleared");
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}