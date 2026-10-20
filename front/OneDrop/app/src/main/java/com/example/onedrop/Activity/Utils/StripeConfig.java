package com.example.onedrop.Activity.Utils;

/**
 * StripeConfig
 * Central place for all Stripe credentials.
 * ⚠️  In production, load the secret key from a secure server-side config —
 *     never ship the real secret key inside an APK.
 *     The publishable key is safe to include in the app.
 */
public class StripeConfig {

    // ── Publishable key (safe in APK) ──────────────────────────────────────
    // Used by the Stripe Android SDK to tokenise the card on the device.
    public static final String PUBLISHABLE_KEY =
            "";

    // ── Secret key (SERVER-SIDE ONLY) ──────────────────────────────────────
    // Kept here only because this project uses a direct servlet backend.
    // Move this to your servlet / backend config file in production.
    public static final String SECRET_KEY =
            "";

    // ── Currency ───────────────────────────────────────────────────────────
    public static final String CURRENCY = "usd";

    private StripeConfig() {} // utility class — no instances
}