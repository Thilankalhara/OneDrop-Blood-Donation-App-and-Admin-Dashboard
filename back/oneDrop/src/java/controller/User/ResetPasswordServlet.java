package controller.User;

import database.connectionProvider;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;

@WebServlet("/ResetPassword")
public class ResetPasswordServlet extends HttpServlet {

    private static final String TAG = "ResetPasswordServlet";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json");
        PrintWriter out = null;

        try {
            out = response.getWriter();
            System.out.println(TAG + " - Request received to reset password.");

            // Parse email and new password from the request
            String email = request.getParameter("email");
            String password = request.getParameter("password");

            if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty() || password.length() < 6) {
                out.write("{\"status\": false, \"message\": \"Valid email and password (minimum 6 characters) are required.\"}");
                return;
            }

            // Trim and normalize email
            email = email.trim().toLowerCase();

            // Update the user's password in the database
            try (Connection conn = connectionProvider.getCon()) {
                if (conn == null) {
                    System.out.println(TAG + " - Failed to initialize database connection.");
                    out.write("{\"status\": false, \"message\": \"Database connection error.\"}");
                    return;
                }

                String query = "UPDATE users SET password = ? WHERE LOWER(email) = ?";
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.setString(1, password); // Use hashing for production passwords
                    stmt.setString(2, email);
                    stmt.executeUpdate();

                    System.out.println(TAG + " - Password reset successfully for email: " + email);
                    out.write("{\"status\": true, \"message\": \"Password reset successfully. Redirecting.\"}");
                }
            } catch (Exception e) {
                System.err.println(TAG + " - Error updating database: " + e.getMessage());
                out.write("{\"status\": false, \"message\": \"Server error. Try again later.\"}");
            }
        } catch (Exception e) {
            System.err.println(TAG + " - Unexpected error occurred: " + e.getMessage());
            if (out != null) {
                out.write("{\"status\": false, \"message\": \"Unexpected error occurred.\"}");
            }
        }
    }
}