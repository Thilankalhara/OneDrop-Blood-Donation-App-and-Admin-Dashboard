package com.example.onedrop.Activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SignUpActivity extends AppCompatActivity {

    private EditText nameField, emailField, passwordField;
    private Button signUpButton;
    private TextView signInLink, termsLink;
    private CheckBox termsCheckbox;
    private ProgressBar loadingProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize UI components
        nameField = findViewById(R.id.nameField);
        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        signUpButton = findViewById(R.id.signUpButton);
        signInLink = findViewById(R.id.signInLink);
        termsCheckbox = findViewById(R.id.termsCheckbox);
        termsLink = findViewById(R.id.termsLink);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);

        // Redirect to SignInActivity
        signInLink.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
            finish();
        });

        // Handle Terms and Conditions link click
        termsLink.setOnClickListener(v -> {
            showTermsAndConditionsDialog();
        });

        // Enable/disable Sign Up button based on checkbox state (visual only)
        termsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Update button appearance based on checkbox state
            updateButtonState();
        });

        // Handle "Sign Up" button click
        signUpButton.setOnClickListener(v -> {
            animateButton(signUpButton);
            validateAndSignUp();
        });

        // Set initial button state
        updateButtonState();
    }

    private void updateButtonState() {
        if (termsCheckbox.isChecked()) {
            signUpButton.setAlpha(1.0f);
        } else {
            signUpButton.setAlpha(0.5f);
        }
    }

    private void showTermsAndConditionsDialog() {
        String termsAndConditions = "<h3>Terms and Conditions</h3>" +
                "<p><b>Last Updated: January 27, 2026</b></p>" +
                "<p>Welcome to OneDrop. By using our application, you agree to comply with and be bound by the following terms and conditions.</p>" +
                "<h4>1. Acceptance of Terms</h4>" +
                "<p>By accessing and using this application, you accept and agree to be bound by the terms and provision of this agreement.</p>" +
                "<h4>2. Use License</h4>" +
                "<p>Permission is granted to temporarily use the application for personal, non-commercial transitory viewing only.</p>" +
                "<h4>3. User Account</h4>" +
                "<p>You are responsible for maintaining the confidentiality of your account and password. You agree to accept responsibility for all activities that occur under your account.</p>" +
                "<h4>4. Privacy Policy</h4>" +
                "<p>Your use of OneDrop is also governed by our Privacy Policy. Please review our Privacy Policy, which also governs the application and informs users of our data collection practices.</p>" +
                "<h4>5. Prohibited Uses</h4>" +
                "<p>You may not use the application:</p>" +
                "<ul>" +
                "<li>For any unlawful purpose</li>" +
                "<li>To solicit others to perform or participate in any unlawful acts</li>" +
                "<li>To violate any international, federal, provincial or state regulations, rules, laws, or local ordinances</li>" +
                "<li>To infringe upon or violate our intellectual property rights or the intellectual property rights of others</li>" +
                "</ul>" +
                "<h4>6. Disclaimer</h4>" +
                "<p>The materials in the application are provided on an 'as is' basis. OneDrop makes no warranties, expressed or implied, and hereby disclaims and negates all other warranties.</p>" +
                "<h4>7. Limitations</h4>" +
                "<p>In no event shall OneDrop or its suppliers be liable for any damages (including, without limitation, damages for loss of data or profit, or due to business interruption) arising out of the use or inability to use the application.</p>" +
                "<h4>8. Modifications</h4>" +
                "<p>OneDrop may revise these terms of service for its application at any time without notice. By using this application you are agreeing to be bound by the then current version of these terms of service.</p>" +
                "<h4>9. Contact Information</h4>" +
                "<p>If you have any questions about these Terms and Conditions, please contact us at:</p>" +
                "<p><b>Email:</b> support@onedrop.com<br>" +
                "<b>Phone:</b> +1 (555) 123-4567</p>";

        new AlertDialog.Builder(this)
                .setTitle("Terms and Conditions")
                .setMessage(Html.fromHtml(termsAndConditions, Html.FROM_HTML_MODE_LEGACY))
                .setPositiveButton("I Agree", (dialog, which) -> {
                    // Automatically check the checkbox when user clicks "I Agree"
                    termsCheckbox.setChecked(true);
                    dialog.dismiss();
                })
                .setNegativeButton("Close", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    private void validateAndSignUp() {
        // Clear previous errors
        nameField.setError(null);
        emailField.setError(null);
        passwordField.setError(null);

        String name = nameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        // Validation flags
        boolean isValid = true;

        // Validate Name
        if (name.isEmpty()) {
            nameField.setError("Name is required");
            nameField.requestFocus();
            return;
        } else if (name.length() < 2) {
            nameField.setError("Name must be at least 2 characters");
            nameField.requestFocus();
            return;
        }

        // Validate Email
        if (email.isEmpty()) {
            emailField.setError("Email is required");
            emailField.requestFocus();
            return;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailField.setError("Please enter a valid email address");
            emailField.requestFocus();
            return;
        }

        // Validate Password
        if (password.isEmpty()) {
            passwordField.setError("Password is required");
            passwordField.requestFocus();
            return;
        } else if (password.length() < 6) {
            passwordField.setError("Password must be at least 6 characters");
            passwordField.requestFocus();
            return;
        }

        // Validate Terms and Conditions
        if (!termsCheckbox.isChecked()) {
            showTermsNotAgreedDialog();
            return;
        }

        // If all validations pass, proceed with sign-up
        performSignUpRequest(name, email, password);
    }

    private void showTermsNotAgreedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Terms and Conditions Required")
                .setMessage("You must agree to the Terms and Conditions to create an account. Please read and accept the terms to continue.")
                .setPositiveButton("Read Terms", (dialog, which) -> {
                    dialog.dismiss();
                    showTermsAndConditionsDialog();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            // Show loading indicator
            loadingProgressBar.setVisibility(View.VISIBLE);

            // Disable button and change text
            signUpButton.setEnabled(false);
            signUpButton.setText("Processing...");

            // Disable input fields
            nameField.setEnabled(false);
            emailField.setEnabled(false);
            passwordField.setEnabled(false);
            termsCheckbox.setEnabled(false);
            termsLink.setEnabled(false);
            signInLink.setEnabled(false);
        } else {
            // Hide loading indicator
            loadingProgressBar.setVisibility(View.GONE);

            // Enable button and restore text
            signUpButton.setText("Sign Up");
            signUpButton.setEnabled(true);

            // Enable input fields
            nameField.setEnabled(true);
            emailField.setEnabled(true);
            passwordField.setEnabled(true);
            termsCheckbox.setEnabled(true);
            termsLink.setEnabled(true);
            signInLink.setEnabled(true);

            // Update button state
            updateButtonState();
        }
    }

    private void performSignUpRequest(String name, String email, String password) {
        // Show loading animation
        setLoading(true);

        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                // Connect to the backend endpoint
                URL url = new URL(Constants.BASE_URL + "UserSignUpServlet");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);

                // Create JSON payload
                String payload = "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.writeBytes(payload);
                    dos.flush();
                }

                // Read the response
                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        responseCode < HttpURLConnection.HTTP_BAD_REQUEST
                                ? conn.getInputStream()
                                : conn.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                }

                String responseString = response.toString();
                Log.d("SIGNUP_BACKEND_RESPONSE", "Response Code: " + responseCode + ", Body: " + responseString);

                // Parse the JSON response
                JSONObject responseJson = new JSONObject(responseString);
                boolean status = responseJson.optBoolean("status", false);
                String message = responseJson.optString("message", "An error occurred. Please try again.");

                // Handle redirection based on response
                if (status && responseCode == HttpURLConnection.HTTP_CREATED) {
                    runOnUiThread(() -> {
                        setLoading(false);
                        Toast.makeText(SignUpActivity.this, "Sign-Up successful! Redirecting to verify your email.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(SignUpActivity.this, VerifyEmailActivity.class);
                        intent.putExtra("email", email); // Pass the email to VerifyEmailActivity
                        startActivity(intent);
                        finish();
                    });
                } else {
                    runOnUiThread(() -> {
                        setLoading(false);
                        showErrorDialog(message);
                    });
                }

            } catch (IOException e) {
                Log.e("SIGNUP_NETWORK_ERROR", "Network error: " + e.getMessage());
                runOnUiThread(() -> {
                    setLoading(false);
                    showErrorDialog("Unable to connect to server. Please check your internet connection and try again.");
                });
            } catch (Exception e) {
                Log.e("SIGNUP_ERROR", "An unexpected error occurred: " + e.getMessage());
                runOnUiThread(() -> {
                    setLoading(false);
                    showErrorDialog("Something went wrong. Please try again later.");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void showErrorDialog(String message) {
        runOnUiThread(() -> new AlertDialog.Builder(SignUpActivity.this)
                .setTitle("Sign-Up Failed")
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show());
    }

    private void animateButton(View button) {
        AlphaAnimation animation = new AlphaAnimation(1.0f, 0.5f);
        animation.setDuration(100);
        animation.setRepeatCount(1);
        animation.setRepeatMode(AlphaAnimation.REVERSE);
        button.startAnimation(animation);
    }
}