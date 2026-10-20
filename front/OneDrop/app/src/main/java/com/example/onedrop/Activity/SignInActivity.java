package com.example.onedrop.Activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.onedrop.Activity.Utils.AuthManager;
import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SignInActivity extends AppCompatActivity {

    private static final String TAG = "SignInActivity";

    private EditText          emailField, passwordField;
    private TextView          signInButton, signUpLink, forgotPasswordLink;
    private CheckBox          rememberMeCheckbox;
    private SharedPreferences sharedPreferences;
    private boolean           isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE);

        // ✅ Already authenticated — check BOTH the JWT token AND the isAuthenticated flag.
        if (AuthManager.isLoggedIn(this)
                && sharedPreferences.getBoolean("isAuthenticated", false)) {
            redirectToMainActivity();
            return;
        }

        emailField         = findViewById(R.id.emailField);
        passwordField      = findViewById(R.id.passwordField);
        signInButton       = findViewById(R.id.signInButton);
        rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox);
        signUpLink         = findViewById(R.id.signUpLink);
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink);

        if (getIntent().getBooleanExtra("verified", false)) {
            Toast.makeText(this, "Account verified! Please sign in.", Toast.LENGTH_LONG).show();
        }

        signInButton.setOnClickListener(v -> {
            if (isLoading) return;
            animateButtonPress(signInButton);
            validateAndSignIn();
        });

        signUpLink.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));

        forgotPasswordLink.setOnClickListener(v ->
                startActivity(new Intent(this, ForgetPasswordActivity.class)));
    }

    private void validateAndSignIn() {
        String email    = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.setError("Enter a valid email"); return;
        }
        if (password.isEmpty()) {
            passwordField.setError("Password is required"); return;
        }

        setLoading(true);
        performSignInRequest(email, password);
    }

    private void performSignInRequest(String email, String password) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.USER_SIGNIN_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept",       "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                String jsonBody = "{\"email\":\"" + email
                        + "\",\"password\":\"" + password + "\"}";

                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.write(jsonBody.getBytes("UTF-8"));
                }

                int    code   = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 400
                                ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                final String resp = sb.toString();
                final int    fc   = code;
                runOnUiThread(() -> handleServerResponse(fc, resp));

            } catch (Exception e) {
                Log.e(TAG, "Network error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Network error. Check your connection.",
                            Toast.LENGTH_LONG).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void handleServerResponse(int code, String responseBody) {
        setLoading(false);
        try {
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("📱 SignInActivity.handleServerResponse()");
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("📊 Response Code: " + code);
            System.out.println("📥 Response Body: " + responseBody.substring(0, Math.min(200, responseBody.length())));

            JSONObject json    = new JSONObject(responseBody);
            String     status  = json.optString("status",  "");
            String     message = json.optString("message", "Something went wrong.");

            System.out.println("🔄 Status: " + status);

            switch (status) {

                case "success":
                    String token  = json.optString("token",  "");
                    int    userId = json.optInt("userId",    -1);
                    String name   = json.optString("name",   "");
                    String email  = json.optString("email",  "");

                    System.out.println("✅ Login successful!");
                    System.out.println("   🔐 Token length: " + token.length());
                    System.out.println("   👤 userId: " + userId);
                    System.out.println("   📧 email: " + email);
                    System.out.println("   📝 name: " + name);

                    // ✅ Save session via AuthManager
                    System.out.println("💾 Saving session...");
                    AuthManager.saveSession(this, token, userId, name, email);

                    // ✅ Verify token was saved
                    String savedToken = AuthManager.getToken(this);
                    System.out.println("✅ Verification — saved token length: " + (savedToken != null ? savedToken.length() : 0));

                    // ✅ Save additional data
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("isAuthenticated", true);
                    editor.putString("blood_type", json.optString("blood_type", ""));
                    editor.putString("profile_image", json.optString("profile_image", ""));
                    editor.putString("created_at", json.optString("created_at", ""));
                    editor.apply();

                    getSharedPreferences("OneDropPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("onboarding_completed", true)
                            .putBoolean("remembered", true)
                            .apply();

                    System.out.println("✅ All data saved successfully");
                    Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
                    redirectToMainActivity();
                    break;

                case "unverified":
                    System.out.println("⚠️  Email not verified");
                    Toast.makeText(this, "Please verify your email first.",
                            Toast.LENGTH_LONG).show();
                    Intent verifyIntent = new Intent(this, VerifyEmailActivity.class);
                    verifyIntent.putExtra("email", json.optString("email", ""));
                    startActivity(verifyIntent);
                    break;

                case "inactive":
                    System.out.println("❌ Account inactive");
                    new AlertDialog.Builder(this)
                            .setTitle("🚫 Account Deactivated")
                            .setMessage("Your account has been deactivated.\n\n" +
                                    "If you think this is a mistake, please contact support:\n\n" +
                                    "📧 thilankalhara8@gmail.com")
                            .setPositiveButton("Contact Support", (dialog, which) -> {
                                // Open email client
                                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                                emailIntent.setType("message/rfc822");
                                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"thilankalhara8@gmail.com"});
                                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Account Deactivation Issue");
                                emailIntent.putExtra(Intent.EXTRA_TEXT, "Hello,\n\n" +
                                        "My account has been deactivated. Please help me reactivate it.\n\n" +
                                        "Email: " + emailField.getText().toString() + "\n\n" +
                                        "Thank you.");
                                try {
                                    startActivity(Intent.createChooser(emailIntent, "Send Email"));
                                } catch (Exception e) {
                                    Toast.makeText(SignInActivity.this,
                                            "No email client found. Please contact thilankalhara8@gmail.com manually.",
                                            Toast.LENGTH_LONG).show();
                                }
                            })
                            .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                            .setCancelable(true)
                            .show();
                    break;

                case "error":
                default:
                    System.out.println("❌ Login error: " + message);
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    break;
            }

        } catch (Exception e) {
            System.out.println("❌ Parse error: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Response error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        System.out.println("════════════════════════════════════════════════════════════");
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        signInButton.setClickable(!loading);
        signInButton.setEnabled(!loading);
        if (loading) {
            signInButton.setText("Signing in...");
            pulseAnimation(signInButton);
        } else {
            signInButton.clearAnimation();
            signInButton.setText("Sign In");
        }
    }

    private void redirectToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void pulseAnimation(View view) {
        AlphaAnimation pulse = new AlphaAnimation(1f, 0.35f);
        pulse.setDuration(600);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        view.startAnimation(pulse);
    }

    private void animateButtonPress(View view) {
        ScaleAnimation scale = new ScaleAnimation(
                1f, 0.95f, 1f, 0.95f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(100);
        scale.setRepeatCount(1);
        scale.setRepeatMode(Animation.REVERSE);
        view.startAnimation(scale);
    }
}