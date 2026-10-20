package com.example.onedrop.Activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ForgetPasswordActivity extends AppCompatActivity {

    private EditText emailInput;
    private Button continueButton;
    private ProgressBar loadingIndicator;
    private TextView signUpLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_account);

        emailInput       = findViewById(R.id.emailInput);
        continueButton   = findViewById(R.id.btnContinue);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        signUpLink       = findViewById(R.id.signUpLink);

        signUpLink.setOnClickListener(v ->
                startActivity(new Intent(ForgetPasswordActivity.this, SignUpActivity.class))
        );

        continueButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (!isValidEmail(email)) {
                emailInput.setError("Enter a valid email.");
                return;
            }
            sendOtpRequest(email);
        });
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void sendOtpRequest(String email) {
        runOnUiThread(() -> {
            loadingIndicator.setVisibility(View.VISIBLE);
            continueButton.setVisibility(View.GONE);
        });

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.BASE_URL + "ForgetPassword");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String params = "email=" + email;
                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.writeBytes(params);
                    dos.flush();
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();

                BufferedReader br = new BufferedReader(new InputStreamReader(
                        responseCode < 400 ? conn.getInputStream() : conn.getErrorStream(),
                        "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                String body = response.toString();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> {
                        loadingIndicator.setVisibility(View.GONE);
                        continueButton.setVisibility(View.VISIBLE);

                        if (body.contains("\"status\": true") || body.contains("\"status\":true")) {
                            Toast.makeText(ForgetPasswordActivity.this,
                                    "OTP sent to your email.", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(ForgetPasswordActivity.this, EnterCodeActivity.class);
                            intent.putExtra("email", email);
                            startActivity(intent);
                            finish();
                        } else {
                            showErrorDialog(extractMessage(body));
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        loadingIndicator.setVisibility(View.GONE);
                        continueButton.setVisibility(View.VISIBLE);
                        showErrorDialog("Server error. Please try again.");
                    });
                }

            } catch (IOException e) {
                runOnUiThread(() -> {
                    loadingIndicator.setVisibility(View.GONE);
                    continueButton.setVisibility(View.VISIBLE);
                    showErrorDialog("Network error. Check your connection.");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ✅ Fixed — no more crash when message key is missing
    private String extractMessage(String response) {
        try {
            // Try "message":"value" format
            int start = response.indexOf("\"message\":\"");
            if (start != -1) {
                start += 11; // skip past "message":"
                int end = response.indexOf("\"", start);
                if (end != -1) {
                    return response.substring(start, end);
                }
            }
            // Try "message": "value" format (with space)
            start = response.indexOf("\"message\": \"");
            if (start != -1) {
                start += 12;
                int end = response.indexOf("\"", start);
                if (end != -1) {
                    return response.substring(start, end);
                }
            }
            return "Something went wrong. Please try again.";
        } catch (Exception e) {
            return "Something went wrong. Please try again.";
        }
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(ForgetPasswordActivity.this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }
}