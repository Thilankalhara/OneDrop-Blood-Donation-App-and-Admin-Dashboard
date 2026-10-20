package config;

public final class StripeKeyConfig {

    // ── Secret key (server-side ONLY — never sent to the app) ─────────────
    public static final String SECRET_KEY =
            System.getenv("STRIPE_SECRET_KEY") != null ? System.getenv("STRIPE_SECRET_KEY") : "YOUR_STRIPE_SECRET_KEY";

    // ── Publishable key (safe to send to the Android app) ─────────────────
    public static final String PUBLISHABLE_KEY =
            System.getenv("STRIPE_PUBLISHABLE_KEY") != null ? System.getenv("STRIPE_PUBLISHABLE_KEY") : "YOUR_STRIPE_PUBLISHABLE_KEY";

    // ── Currency ───────────────────────────────────────────────────────────
    public static final String CURRENCY = "usd";

    // ── Prevent instantiation ──────────────────────────────────────────────
    private StripeKeyConfig() {}
}