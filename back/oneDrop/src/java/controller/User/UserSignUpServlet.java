package controller.User;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import database.connectionProvider;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

@WebServlet("/UserSignUpServlet")
public class UserSignUpServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(UserSignUpServlet.class.getName());
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final Gson GSON = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        LOGGER.info("=== UserSignUpServlet.doPost() called ===");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Accept");

        JsonObject jsonResponse = new JsonObject();
        String name = null, email = null, password = null;

        try (BufferedReader reader = request.getReader()) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }

            LOGGER.info("Request JSON: " + requestBody.toString());

            if (requestBody.length() == 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.addProperty("status", false);
                jsonResponse.addProperty("message", "Empty request body.");
                sendJsonResponse(response, jsonResponse);
                return;
            }

            JsonObject requestJson = GSON.fromJson(requestBody.toString(), JsonObject.class);
            name = requestJson.get("name").getAsString().trim();
            email = requestJson.get("email").getAsString().trim();
            password = requestJson.get("password").getAsString().trim();

        } catch (JsonSyntaxException e) {
            LOGGER.log(Level.WARNING, "Invalid JSON syntax", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.addProperty("status", false);
            jsonResponse.addProperty("message", "Invalid JSON format.");
            sendJsonResponse(response, jsonResponse);
            return;
        }

        // Validate inputs
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.addProperty("status", false);
            jsonResponse.addProperty("message", "Name, Email, and Password are required.");
            sendJsonResponse(response, jsonResponse);
            return;
        }

        if (!Pattern.matches(EMAIL_REGEX, email)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.addProperty("status", false);
            jsonResponse.addProperty("message", "Invalid email format.");
            sendJsonResponse(response, jsonResponse);
            return;
        }

        LOGGER.info("Validation successful for: " + email);

        // Database operations
        try (Connection conn = connectionProvider.getCon()) {
            if (conn == null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                jsonResponse.addProperty("status", false);
                jsonResponse.addProperty("message", "Database connection failed.");
                sendJsonResponse(response, jsonResponse);
                return;
            }

            LOGGER.info("Database connected!");

            // Check if email exists
            String checkUserSQL = "SELECT email FROM users WHERE email = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkUserSQL)) {
                checkStmt.setString(1, email);
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    LOGGER.warning("Email already exists: " + email);
                    response.setStatus(HttpServletResponse.SC_CONFLICT);
                    jsonResponse.addProperty("status", false);
                    jsonResponse.addProperty("message", "Email already exists!");
                    sendJsonResponse(response, jsonResponse);
                    return;
                }
            }

            // Insert new user into the database
            String insertSQL = "INSERT INTO users (name, email, password, email_verified) VALUES (?, ?, ?, false)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {
                insertStmt.setString(1, name);
                insertStmt.setString(2, email);
                insertStmt.setString(3, password);
                int insertedRows = insertStmt.executeUpdate();
                if (insertedRows > 0) {
                    LOGGER.info("User created successfully: " + email);

                    // Call SendVerificationCodeServlet
                    boolean verificationSent = callSendVerificationCode(email);
                    if (verificationSent) {
                        jsonResponse.addProperty("status", true);
                        jsonResponse.addProperty("message", "User created successfully and verification code sent!");
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        sendJsonResponse(response, jsonResponse);
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        jsonResponse.addProperty("status", false);
                        jsonResponse.addProperty("message", "User created, but failed to send verification code.");
                        sendJsonResponse(response, jsonResponse);
                    }
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    jsonResponse.addProperty("status", false);
                    jsonResponse.addProperty("message", "Unable to create the user. Please try again.");
                    sendJsonResponse(response, jsonResponse);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Database error.", ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.addProperty("status", false);
            jsonResponse.addProperty("message", "Database error. Please try again later.");
            sendJsonResponse(response, jsonResponse);
        } catch (Exception ex) {
            Logger.getLogger(UserSignUpServlet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private boolean callSendVerificationCode(String email) {
        try {
            URL url = new URL("http://localhost:8080/oneDrop/SendVerificationCodeServlet"); // Update as per deployment
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            JsonObject emailRequest = new JsonObject();
            emailRequest.addProperty("email", email);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(emailRequest.toString().getBytes("UTF-8"));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            LOGGER.info("SendVerificationCodeServlet Response Code: " + responseCode);

            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to call SendVerificationCodeServlet.", e);
            return false;
        }
    }

    private void sendJsonResponse(HttpServletResponse response, JsonObject jsonResponse) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.print(jsonResponse.toString());
            out.flush();
        }
    }
}