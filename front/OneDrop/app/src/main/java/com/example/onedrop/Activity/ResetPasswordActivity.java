package com.example.onedrop.Activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
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

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText newPasswordInput, confirmPasswordInput;
    private Button confirmButton;

    private String userEmail; // Pass email from previous screen

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        // Initialize UI components
        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        confirmButton = findViewById(R.id.btnConfirm);

        // Retrieve email from Intent
        userEmail = getIntent().getStringExtra("email");

        confirmButton.setOnClickListener(v -> {
            String password = newPasswordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();

            if (password.isEmpty() || password.length() < 6) {
                newPasswordInput.setError("Password must be at least 6 characters.");
                return;
            }
            if (!password.equals(confirmPassword)) {
                confirmPasswordInput.setError("Passwords do not match.");
                return;
            }
            resetPassword(password);
        });
    }

    private void resetPassword(String password) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.BASE_URL + "ResetPassword");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);

                // Send email and password to backend
                String params = "email=" + userEmail + "&password=" + password;
                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.writeBytes(params);
                    dos.flush();
                }

                // Read response
                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }

                System.out.println("Backend Response: " + response);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    if (response.toString().contains("\"status\": true")) {
                        runOnUiThread(() -> {
                            Toast.makeText(ResetPasswordActivity.this, "Password reset successfully.", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(ResetPasswordActivity.this, SignInActivity.class); // Redirect to SignupActivity
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        String message = extractMessage(response.toString());
                        runOnUiThread(() -> showErrorDialog(message));
                    }
                } else {
                    runOnUiThread(() -> showErrorDialog("Unexpected error. Please try again."));
                }
            } catch (IOException e) {
                runOnUiThread(() -> showErrorDialog("Network error. Check your connection."));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String extractMessage(String response) {
        int messageStart = response.indexOf("\"message\":\"") + 10;
        int messageEnd = response.indexOf("\"", messageStart);
        return response.substring(messageStart, messageEnd);
    }

    private void showErrorDialog(String message) {
        new AlertDialog.Builder(ResetPasswordActivity.this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss())
                .show();
    }
}