package controller.User;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import util.JwtUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import database.connectionProvider;

@WebServlet("/UserSignInServlet")
public class UserSignInServlet extends HttpServlet {

    private static final Logger LOGGER      = Logger.getLogger(UserSignInServlet.class.getName());
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final Gson   gson        = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        JsonObject jsonResponse = new JsonObject();
        String email = null, password = null;

        // ── 1. Parse body ──────────────────────────────────────────────────
        try {
            StringBuilder requestBody = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) requestBody.append(line);
            }
            LOGGER.info("SignIn request: " + requestBody);

            JsonObject requestJson = gson.fromJson(requestBody.toString(), JsonObject.class);
            email    = requestJson.get("email").getAsString().trim();
            password = requestJson.get("password").getAsString().trim();

        } catch (JsonSyntaxException | IOException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.addProperty("status",  "error");
            jsonResponse.addProperty("message", "Invalid JSON format.");
            sendResponse(response, jsonResponse);
            return;
        }

        // ── 2. Validation ──────────────────────────────────────────────────
        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.addProperty("status",  "error");
            jsonResponse.addProperty("message", "Email and Password are required.");
            sendResponse(response, jsonResponse);
            return;
        }

        if (!Pattern.matches(EMAIL_REGEX, email)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.addProperty("status",  "error");
            jsonResponse.addProperty("message", "Invalid email format.");
            sendResponse(response, jsonResponse);
            return;
        }

        // ── 3. DB lookup ───────────────────────────────────────────────────
        try {
            Connection conn = connectionProvider.getCon();

            String query =
                "SELECT user_id, name, email, blood_type, profile_image, " +
                "status, email_verified, created_at " +
                "FROM users WHERE email = ? AND password = ?";

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {

                String  accountStatus = rs.getString("status");
                boolean emailVerified = rs.getBoolean("email_verified");

                // ── INACTIVE ───────────────────────────────────────────────
                if ("INACTIVE".equals(accountStatus)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    jsonResponse.addProperty("status",  "inactive");
                    jsonResponse.addProperty("message",
                            "Your account has been deactivated.\n\n" +
                            "If you believe this is a mistake, please contact support at:\n" +
                            "thilankalhara8@gmail.com");
                    rs.close(); stmt.close();
                    sendResponse(response, jsonResponse);
                    return;
                }

                // ── Email not verified ─────────────────────────────────────
                if (!emailVerified) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    jsonResponse.addProperty("status",  "unverified");
                    jsonResponse.addProperty("message", "Please verify your email before signing in.");
                    jsonResponse.addProperty("email",   rs.getString("email"));
                    rs.close(); stmt.close();
                    sendResponse(response, jsonResponse);
                    return;
                }

                // ── Success ────────────────────────────────────────────────
                int    userId    = rs.getInt("user_id");
                String name      = nvl(rs.getString("name"));
                String emailVal  = nvl(rs.getString("email"));
                String bloodType = nvl(rs.getString("blood_type"));
                String createdAt = nvl(rs.getString("created_at"));
                String profileImg = rs.getString("profile_image") != null
                        ? rs.getString("profile_image").replaceAll("\\s+", "") : "";

                // ✅ Generate JWT — signed with secret, contains userId, expires in 7 days
                String token = JwtUtil.generateToken(userId);
                LOGGER.info("JWT generated for userId=" + userId);

                response.setStatus(HttpServletResponse.SC_OK);
                jsonResponse.addProperty("status",        "success");
                jsonResponse.addProperty("message",       "Login successful.");
                jsonResponse.addProperty("token",         token);      // ✅ JWT token
                jsonResponse.addProperty("userId",        userId);
                jsonResponse.addProperty("name",          name);
                jsonResponse.addProperty("email",         emailVal);
                jsonResponse.addProperty("blood_type",    bloodType);
                jsonResponse.addProperty("profile_image", profileImg);
                jsonResponse.addProperty("created_at",    createdAt);

                LOGGER.info("Login success — userId: " + userId);

            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                jsonResponse.addProperty("status",  "error");
                jsonResponse.addProperty("message", "Invalid email or password.");
            }

            rs.close(); stmt.close();

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB error during login.", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.addProperty("status",  "error");
            jsonResponse.addProperty("message", "Server error. Please try again.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during login.", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.addProperty("status",  "error");
            jsonResponse.addProperty("message", "Unexpected error occurred.");
        }

        sendResponse(response, jsonResponse);
    }

    private void sendResponse(HttpServletResponse response, JsonObject json) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.print(json.toString());
            out.flush();
        }
    }

    private String nvl(String s) { return s != null ? s : ""; }
}