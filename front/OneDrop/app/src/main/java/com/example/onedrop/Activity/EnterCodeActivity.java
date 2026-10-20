package com.example.onedrop.Activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class EnterCodeActivity extends AppCompatActivity {

    private TextView signUpLink;
    private EditText codeInput;
    private Button verifyButton, resendButton;
    private String userEmail;
    private boolean otpVerified = false;
    private CountDownTimer resendCountDown;
    private boolean canResend = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_code);

        // Initialize UI elements
        codeInput = findViewById(R.id.codeInput);
        verifyButton = findViewById(R.id.btnVerify);
        resendButton = findViewById(R.id.btnResend);
        signUpLink = findViewById(R.id.signUpLink);

        userEmail = getIntent().getStringExtra("email");

        signUpLink.setOnClickListener(v ->
                startActivity(new Intent(EnterCodeActivity.this, SignUpActivity.class))
        );

        // Handle verify button click
        verifyButton.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            if (code.isEmpty()) {
                codeInput.setError("Please enter an OTP.");
                return;
            }
            if (code.length() != 6) {
                codeInput.setError("Please enter a valid 6-digit OTP.");
                return;
            }
            verifyOtp(code);
        });

        // Start countdown immediately since OTP was just sent
        startResendCountdown();

        // Handle resend button click
        resendButton.setOnClickListener(v -> {
            if (!canResend) return;
            resendOtp();
            startResendCountdown();
        });

        // Customize Back Button Logic
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!otpVerified) {
                    Toast.makeText(EnterCodeActivity.this, "You must verify your OTP first.", Toast.LENGTH_SHORT).show();
                } else {
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void startResendCountdown() {
        canResend = false;
        resendButton.setEnabled(false);
        resendButton.setTextColor(Color.parseColor("#999999"));

        if (resendCountDown != null) resendCountDown.cancel();

        resendCountDown = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                resendButton.setText("Resend in " + seconds + "s");
            }

            @Override
            public void onFinish() {
                canResend = true;
                resendButton.setEnabled(true);
                resendButton.setText("Resend OTP");
                resendButton.setTextColor(Color.parseColor("#1565C0")); // holo_blue_dark equivalent
            }
        }.start();
    }

    /**
     * Verifies the OTP via backend.
     * Sends a POST request to the server and handles the result.
     */
    private void verifyOtp(String code) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.VERIFY_OTP_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                String postData = "email=" + userEmail + "&code=" + code;
                System.out.println("Sending OTP verification request: " + postData);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                System.out.println("Response from server: " + response);

                JSONObject jsonResponse = new JSONObject(response.toString());
                boolean status = jsonResponse.getBoolean("status");
                String message = jsonResponse.getString("message");

                runOnUiThread(() -> {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    if (status) {
                        otpVerified = true;
                        Intent intent = new Intent(this, ResetPasswordActivity.class);
                        intent.putExtra("email", userEmail);
                        startActivity(intent);
                        finish();
                    } else {
                        otpVerified = false;
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to connect to server.", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * Sends a request to backend to resend the OTP.
     * Sends a POST request to the ForgetPasswordServlet and displays the result.
     */
    private void resendOtp() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.FORGET_PASSWORD_ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                String postData = "email=" + userEmail;
                System.out.println("Sending resend OTP request: " + postData);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData.getBytes());
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                System.out.println("Response from server (Resend OTP): " + response);

                JSONObject jsonResponse = new JSONObject(response.toString());
                String message = jsonResponse.getString("message");
                boolean status = jsonResponse.getBoolean("status");

                runOnUiThread(() -> {
                    if (status) {
                        Toast.makeText(this, "OTP resent. Check your email.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to resend OTP. Please try again.", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendCountDown != null) resendCountDown.cancel();
    }
}