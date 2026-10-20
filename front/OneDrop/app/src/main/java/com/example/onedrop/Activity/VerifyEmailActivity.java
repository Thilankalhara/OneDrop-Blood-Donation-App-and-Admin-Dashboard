package com.example.onedrop.Activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VerifyEmailActivity extends AppCompatActivity {

    private static final String TAG = "VerifyEmailActivity";

    private EditText       verificationCodeField;
    private TextView       verifyButton, resendLink, signUpLink;
    private ImageView      backButton;
    private String         userEmail;
    private CountDownTimer resendCountDown;
    private boolean        canResend = false;
    private boolean        isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        // ── Get email from intent ──────────────────────────────────────────
        userEmail = getIntent().getStringExtra("email");
        if (userEmail == null || userEmail.isEmpty()) {
            Toast.makeText(this, "Email not found. Please try again.",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ── Initialize UI ──────────────────────────────────────────────────
        verificationCodeField = findViewById(R.id.verificationCodeField);
        verifyButton          = findViewById(R.id.verifyButton);
        resendLink            = findViewById(R.id.resendLink);
        signUpLink            = findViewById(R.id.signUpLink);
        backButton            = findViewById(R.id.backButton);

        // ── Back button ────────────────────────────────────────────────────
        backButton.setOnClickListener(v -> finish());

        // ── Verify button ──────────────────────────────────────────────────
        verifyButton.setOnClickListener(v -> {
            if (isLoading) return;
            String code = verificationCodeField.getText().toString().trim();
            if (code.isEmpty()) {
                verificationCodeField.setError("Code is required.");
                return;
            }
            if (code.length() != 6) {
                verificationCodeField.setError("Code must be 6 digits.");
                return;
            }
            animateButtonPress(verifyButton);
            setVerifyLoading(true);
            verifyEmailCode(userEmail, code);
        });

        // ── Start 60s countdown (code was just sent) ───────────────────────
        startResendCountdown();

        // ── Resend link ────────────────────────────────────────────────────
        resendLink.setOnClickListener(v -> {
            if (!canResend || isLoading) return;
            animateButtonPress(resendLink);
            resendVerificationCode();
        });

        // ── Back to Sign Up ────────────────────────────────────────────────
        signUpLink.setOnClickListener(v -> {
            startActivity(new Intent(this, SignUpActivity.class));
            finish();
        });
    }

    // ── Verify code with server ────────────────────────────────────────────
    private void verifyEmailCode(String email, String code) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.VERIFY_EMAIL_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("code", code);

                try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                    os.write(payload.toString().getBytes("UTF-8"));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        responseCode < HttpURLConnection.HTTP_BAD_REQUEST
                                ? conn.getInputStream()
                                : conn.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);
                }

                String body = response.toString();
                Log.d(TAG, "Verify → " + responseCode + " | " + body);
                runOnUiThread(() -> handleVerificationResponse(responseCode, body));

            } catch (Exception e) {
                Log.e(TAG, "Network error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    setVerifyLoading(false);
                    Toast.makeText(this, "Network error. Please try again.",
                            Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ── Handle verify response ─────────────────────────────────────────────
    private void handleVerificationResponse(int responseCode, String response) {
        setVerifyLoading(false);
        try {
            JSONObject json    = new JSONObject(response);
            boolean    status  = json.optBoolean("status", false);
            String     message = json.optString("message", "Verification failed. Try again.");

            if (status && responseCode == HttpURLConnection.HTTP_OK) {
                animateButtonSuccess(verifyButton);
                Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, SignInActivity.class);
                intent.putExtra("verified", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            } else {
                shakeButton(verifyButton);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage(), e);
            Toast.makeText(this, "Invalid response from server.", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Resend code — reuses SendVerificationCodeServlet ──────────────────
    private void resendVerificationCode() {
        setResendLoading(true);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.SEND_VERIFICATION_CODE_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                JSONObject payload = new JSONObject();
                payload.put("email", userEmail);

                try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                    os.write(payload.toString().getBytes("UTF-8"));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        responseCode < HttpURLConnection.HTTP_BAD_REQUEST
                                ? conn.getInputStream()
                                : conn.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);
                }

                String body = response.toString();
                Log.d(TAG, "Resend → " + responseCode + " | " + body);

                runOnUiThread(() -> {
                    setResendLoading(false);
                    try {
                        JSONObject json   = new JSONObject(body);
                        boolean    status = json.optBoolean("status", false);
                        String     msg    = json.optString("message", "Failed to resend.");
                        if (status) {
                            Toast.makeText(this,
                                    "Code sent to " + userEmail, Toast.LENGTH_SHORT).show();
                            startResendCountdown();
                        } else {
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                            restoreResendLink();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to resend code.", Toast.LENGTH_SHORT).show();
                        restoreResendLink();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Resend error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    setResendLoading(false);
                    Toast.makeText(this, "Network error. Please try again.",
                            Toast.LENGTH_SHORT).show();
                    restoreResendLink();
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ── Countdown 60s ─────────────────────────────────────────────────────
    private void startResendCountdown() {
        canResend = false;
        resendLink.setClickable(false);
        resendLink.setTextColor(Color.parseColor("#999999"));
        if (resendCountDown != null) resendCountDown.cancel();
        resendCountDown = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long ms) {
                resendLink.setText("Resend in " + (ms / 1000) + "s");
            }
            @Override public void onFinish() {
                restoreResendLink();
            }
        }.start();
    }

    private void restoreResendLink() {
        canResend = true;
        resendLink.setClickable(true);
        resendLink.clearAnimation();
        resendLink.setText("Send it again?");
        resendLink.setTextColor(Color.parseColor("#C84A4A"));
    }

    // ── Loading: verify button ─────────────────────────────────────────────
    private void setVerifyLoading(boolean loading) {
        isLoading = loading;
        verifyButton.setClickable(!loading);
        if (loading) {
            verifyButton.setText("Verifying...");
            pulseAnimation(verifyButton);
        } else {
            verifyButton.clearAnimation();
            verifyButton.setText("Verify Email");
        }
    }

    // ── Loading: resend link ───────────────────────────────────────────────
    private void setResendLoading(boolean loading) {
        isLoading = loading;
        resendLink.setClickable(!loading);
        if (loading) {
            resendLink.setText("Sending...");
            resendLink.setTextColor(Color.parseColor("#999999"));
            pulseAnimation(resendLink);
        } else {
            resendLink.clearAnimation();
        }
    }

    // ── Animation: infinite pulse ──────────────────────────────────────────
    private void pulseAnimation(TextView view) {
        AlphaAnimation pulse = new AlphaAnimation(1f, 0.35f);
        pulse.setDuration(600);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);
        view.startAnimation(pulse);
    }

    // ── Animation: scale press on tap ─────────────────────────────────────
    private void animateButtonPress(TextView view) {
        ScaleAnimation scale = new ScaleAnimation(
                1f, 0.95f, 1f, 0.95f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(100);
        scale.setRepeatCount(1);
        scale.setRepeatMode(Animation.REVERSE);
        view.startAnimation(scale);
    }

    // ── Animation: scale up on success ────────────────────────────────────
    private void animateButtonSuccess(TextView view) {
        ScaleAnimation scale = new ScaleAnimation(
                1f, 1.06f, 1f, 1.06f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(150);
        scale.setRepeatCount(1);
        scale.setRepeatMode(Animation.REVERSE);
        view.startAnimation(scale);
    }

    // ── Animation: shake on wrong code ────────────────────────────────────
    private void shakeButton(TextView view) {
        TranslateAnimation shake = new TranslateAnimation(0, 18, 0, 0);
        shake.setDuration(80);
        shake.setRepeatCount(4);
        shake.setRepeatMode(Animation.REVERSE);
        view.startAnimation(shake);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendCountDown != null) resendCountDown.cancel();
    }
}