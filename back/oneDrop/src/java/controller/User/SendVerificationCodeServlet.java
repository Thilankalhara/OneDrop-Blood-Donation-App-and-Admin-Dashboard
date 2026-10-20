package controller.User;

import model.Mail;
import database.connectionProvider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;

@WebServlet("/SendVerificationCodeServlet")
public class SendVerificationCodeServlet extends HttpServlet {

    private static final String TAG = "SendVerificationCodeServlet";
    private static final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = null;

        try {
            out = response.getWriter();

            // Log the start of the request
            System.out.println(TAG + " - Request received for sending email verification code.");

            // Parse email from JSON request body
            StringBuilder requestBody = new StringBuilder();
            String line;
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }

            JsonObject jsonRequest = gson.fromJson(requestBody.toString(), JsonObject.class);
            String email = jsonRequest.get("email").getAsString().trim().toLowerCase();

            System.out.println(TAG + " - Received email: " + email);

            if (email == null || email.isEmpty()) {
                System.out.println(TAG + " - Invalid email: null or empty.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.write("{\"status\": false, \"message\": \"Email is required.\"}");
                return;
            }

            // Normalize email and verify if it exists in the database
            try (Connection conn = connectionProvider.getCon()) {
                if (conn == null) {
                    System.out.println(TAG + " - Database connection error.");
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("{\"status\": false, \"message\": \"Database connection error.\"}");
                    return;
                }
                System.out.println(TAG + " - Connected to the database successfully.");

                // Check if the user exists
                String userQuery = "SELECT user_id FROM users WHERE LOWER(email) = ?";
                int userId;
                try (PreparedStatement stmt = conn.prepareStatement(userQuery)) {
                    stmt.setString(1, email);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        userId = rs.getInt("user_id");
                        System.out.println(TAG + " - User found in database. user_id: " + userId);
                    } else {
                        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        out.write("{\"status\": false, \"message\": \"Email is not registered in the system.\"}");
                        System.out.println(TAG + " - User with email not found: " + email);
                        return;
                    }
                }

                // Generate OTP
                int otp = generateOtp();
                System.out.println(TAG + " - Generated OTP: " + otp);

                // Store OTP in the database
                storeOtp(conn, userId, otp, "EMAIL_VERIFY");

                // Send OTP via Email
                try {
                    Mail.sendMail(
                        email,
                        "Your Email Verification Code",
                        "<h3>Please verify your email address:</h3><p style='font-size:18px; font-weight:bold;'>"
                                + otp + "</p>"
                    );
                    System.out.println(TAG + " - Verification email sent to: " + email);
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.write("{\"status\": true, \"message\": \"Verification code sent successfully.\"}");
                } catch (Exception e) {
                    System.out.println(TAG + " - Error sending email: " + e.getMessage());
                    e.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.write("{\"status\": false, \"message\": \"Failed to send verification email.\"}");
                }
            }
        } catch (Exception ex) {
            System.err.println(TAG + " - Unexpected error occurred: " + ex.getMessage());
            ex.printStackTrace();
            if (out != null) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.write("{\"status\": false, \"message\": \"Unexpected server error occurred.\"}");
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }

        System.out.println(TAG + " - Request processing completed.");
    }

    private int generateOtp() {
        return new Random().nextInt(900000) + 100000; // Generate a 6-digit OTP
    }

    private void storeOtp(Connection conn, int userId, int otp, String tokenType) throws Exception {
        String insertQuery = "INSERT INTO user_tokens (user_id, token, token_type, expires_at, used) " +
                             "VALUES (?, ?, ?, NOW() + INTERVAL 15 MINUTE, FALSE)";

        try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            stmt.setInt(1, userId);
            stmt.setString(2, String.valueOf(otp));
            stmt.setString(3, tokenType);

            System.out.println(TAG + " - Executing insert query: " + stmt.toString());
            int rowsAffected = stmt.executeUpdate();
            System.out.println(TAG + " - Rows affected while inserting OTP: " + rowsAffected);

            if (rowsAffected == 0) {
                throw new Exception("Failed to insert OTP into the database.");
            }
        }
    }
}