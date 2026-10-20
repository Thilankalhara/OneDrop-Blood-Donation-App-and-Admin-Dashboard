package com.example.onedrop.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.onedrop.Activity.Utils.Constants;
import com.example.onedrop.R;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CompleteSignUpActivity extends AppCompatActivity {

    private EditText nameField;
    private Button completeSignUpButton;
    private String email, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complete_sign_up);

        email = getIntent().getStringExtra("email");
        password = getIntent().getStringExtra("password");

        nameField = findViewById(R.id.nameField);
        completeSignUpButton = findViewById(R.id.completeSignUpButton);

        completeSignUpButton.setOnClickListener(v -> completeSignUp());
    }

    private void completeSignUp() {
        String name = nameField.getText().toString().trim();
        if (name.isEmpty()) {
            nameField.setError("Name required");
            return;
        }

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(Constants.BASE_URL + "CompleteSignUpServlet");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; utf-8");

                String payload = String.format("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}", name, email, password);
                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    dos.writeBytes(payload);
                    dos.flush();
                }

                int responseCode = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Toast.makeText(this, "Sign up complete!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(CompleteSignUpActivity.this, SignUpActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Failed to complete sign up", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Server error", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}
