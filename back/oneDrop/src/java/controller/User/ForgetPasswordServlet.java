package controller.User;

import model.Mail;
import database.connectionProvider;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Random;

@WebServlet("/ForgetPassword")
public class ForgetPasswordServlet extends HttpServlet {

    private static final String TAG = "ForgetPasswordServlet";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        PrintWriter out = null;

        try {
            out = response.getWriter();

            // Log the start of the request
            System.out.println(TAG + " - Request received for password reset.");

            // Parse email from request
            String email = request.getParameter("email");
            if (email == null || email.trim().isEmpty()) {
                System.out.println(TAG + " - Received invalid email: null or empty.");
                out.write("{\"status\": false, \"message\": \"Email is required.\"}");
                return;
            }

            // Log email processing
            System.out.println(TAG + " - Received email: " + email);

            // Normalize the email
            email = email.trim().toLowerCase();
            System.out.println(TAG + " - Normalized email: " + email);

            // Verify if the email exists in the database
            try (Connection conn = connectionProvider.getCon()) {
                if (conn == null) {
                    System.out.println(TAG + " - Database connection error.");
                    out.write("{\"status\": false, \"message\": \"Database connection error.\"}");
                    return;
                }
                System.out.println(TAG + " - Connected to the database successfully.");

                String userQuery = "SELECT user_id FROM users WHERE LOWER(email) = ?";
                int userId = -1;
                try (PreparedStatement stmt = conn.prepareStatement(userQuery)) {
                    stmt.setString(1, email);
                    System.out.println(TAG + " - Executing query to check email existence: " + stmt.toString());
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        userId = rs.getInt("user_id");
                        System.out.println(TAG + " - User found with user_id: " + userId);
                    } else {
                        System.out.println(TAG + " - Email not found in database.");
                        out.write("{\"status\": false, \"message\": \"This email does not exist in our system.\"}");
                        return;
                    }
                }

                // Generate OTP
                int otp = generateOtp();
                System.out.println(TAG + " - Generated OTP: " + otp);

                // Store OTP in the database
                storeOtp(conn, userId, otp, "PASSWORD_RESET");

                // Send OTP email
                try {
                    Mail.sendMail(
                        email,
                        "Your Password Reset Code",
                        "<h3>Use the following code to reset your password:</h3>" +
                                "<p style='font-size:18px; font-weight:bold;'>" + otp + "</p>"
                    );
                    System.out.println(TAG + " - OTP sent successfully to: " + email);
                    out.write("{\"status\": true, \"message\": \"OTP has been sent to your email address.\"}");
                } catch (Exception e) {
                    System.err.println(TAG + " - Error sending email: " + e.getMessage());
                    e.printStackTrace();
                    out.write("{\"status\": false, \"message\": \"Failed to send email.\"}");
                }
            }
        } catch (Exception ex) {
            System.err.println(TAG + " - Unexpected error occurred: " + ex.getMessage());
            if (out != null) {
                out.write("{\"status\": false, \"message\": \"Unexpected error occurred.\"}");
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }

        // Log the end of processing
        System.out.println(TAG + " - Request processing completed.");
    }

    private int generateOtp() {
        return new Random().nextInt(900000) + 100000; // Generate a random 6-digit OTP
    }

    private void storeOtp(Connection conn, int userId, int otp, String tokenType) throws Exception {
        String insertQuery = "INSERT INTO user_tokens (user_id, token, token_type, expires_at, used) " +
                             "VALUES (?, ?, ?, NOW() + INTERVAL 15 MINUTE, FALSE)";

        try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            stmt.setInt(1, userId); // User ID from `users` table
            stmt.setString(2, String.valueOf(otp)); // OTP
            stmt.setString(3, tokenType); // Token type (e.g., PASSWORD_RESET)

            System.out.println(TAG + " - Executing insert query: " + stmt);
            int rowsAffected = stmt.executeUpdate();
            System.out.println(TAG + " - Rows affected while inserting OTP: " + rowsAffected);

            if (rowsAffected == 0) {
                throw new Exception("Fail: OTP was not inserted into the database.");
            }
        }
    }
}