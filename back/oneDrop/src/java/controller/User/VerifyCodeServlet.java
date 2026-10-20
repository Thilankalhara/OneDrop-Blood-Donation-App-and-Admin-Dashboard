package controller.User;

import database.connectionProvider;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/VerifyCode")
public class VerifyCodeServlet extends HttpServlet {

    private static final String TAG = "VerifyCodeServlet";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        PrintWriter out = null;

        try {
            out = response.getWriter();
            System.out.println(TAG + " - OTP verification request received.");

            // Parse email and OTP from the request
            String email = request.getParameter("email");
            String otp = request.getParameter("code");

            System.out.println(TAG + " - Email received: " + email);
            System.out.println(TAG + " - Entered OTP: " + otp);

            if (email == null || email.trim().isEmpty() || otp == null || otp.trim().isEmpty()) {
                out.write("{\"status\": false, \"message\": \"Email and OTP are required.\"}");
                System.out.println(TAG + " - Missing email or OTP in the request.");
                return;
            }

            email = email.trim().toLowerCase();

            try (Connection conn = connectionProvider.getCon()) {
                if (conn == null) {
                    System.out.println(TAG + " - Failed to initialize database connection.");
                    out.write("{\"status\": false, \"message\": \"Database connection error.\"}");
                    return;
                }

                System.out.println(TAG + " - Database connection established.");

                // Fetch user_id based on email
                String userIdQuery = "SELECT user_id FROM users WHERE LOWER(email) = ?";
                int userId = -1;

                try (PreparedStatement userStmt = conn.prepareStatement(userIdQuery)) {
                    userStmt.setString(1, email);
                    ResultSet userRs = userStmt.executeQuery();
                    if (userRs.next()) {
                        userId = userRs.getInt("user_id");
                        System.out.println(TAG + " - User found with user_id: " + userId);
                    } else {
                        out.write("{\"status\": false, \"message\": \"Email not registered.\"}");
                        System.out.println(TAG + " - No user found for email: " + email);
                        return;
                    }
                }

                // Verify OTP for the user
                String otpQuery = "SELECT token FROM user_tokens WHERE user_id = ? AND token_type = 'PASSWORD_RESET' AND expires_at > CURRENT_TIMESTAMP AND used = false ORDER BY created_at DESC LIMIT 1";

                try (PreparedStatement otpStmt = conn.prepareStatement(otpQuery)) {
                    otpStmt.setInt(1, userId);
                    System.out.println(TAG + " - Executing OTP query: " + otpStmt.toString());
                    ResultSet otpRs = otpStmt.executeQuery();

                    if (otpRs.next()) {
                        String dbToken = otpRs.getString("token");
                        System.out.println(TAG + " - Retrieved OTP from database: " + dbToken);

                        if (dbToken.equals(otp)) {
                            // Mark OTP as used
                            String updateQuery = "UPDATE user_tokens SET used = true WHERE user_id = ? AND token = ?";
                            try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                                updateStmt.setInt(1, userId);
                                updateStmt.setString(2, otp);
                                int rowsUpdated = updateStmt.executeUpdate();
                                System.out.println(TAG + " - Rows updated by marking OTP as used: " + rowsUpdated);
                            }

                            out.write("{\"status\": true, \"message\": \"OTP verified successfully.\"}");
                            System.out.println(TAG + " - OTP verified successfully.");
                        } else {
                            out.write("{\"status\": false, \"message\": \"Invalid OTP.\"}");
                            System.out.println(TAG + " - OTP mismatch: Entered = " + otp + ", DB = " + dbToken);
                        }
                    } else {
                        out.write("{\"status\": false, \"message\": \"OTP expired or not found.\"}");
                        System.out.println(TAG + " - No valid OTP found for user_id: " + userId);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(TAG + " - Error during OTP verification: " + e.getMessage());
            e.printStackTrace();
            if (out != null) {
                out.write("{\"status\": false, \"message\": \"Server error. Please try again later.\"}");
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {}
            }
        }

        System.out.println(TAG + " - OTP verification process completed.");
    }
}