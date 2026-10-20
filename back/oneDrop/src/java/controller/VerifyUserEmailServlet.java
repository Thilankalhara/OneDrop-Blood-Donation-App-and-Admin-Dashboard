//package controller;
//
//import model.Mail;
//import database.connectionProvider;
//
//
//import javax.mail.MessagingException;
//import javax.servlet.ServletException;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import java.io.IOException;
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.util.UUID;
//import java.util.logging.Level;
//import java.util.logging.Logger;
//
//public class VerifyUserEmailServlet extends HttpServlet {
//
//    private static final Logger LOGGER = Logger.getLogger(VerifyUserEmailServlet.class.getName());
//
//    @Override
//    protected void doPost(HttpServletRequest request, HttpServletResponse response)
//            throws ServletException, IOException {
//
//        response.setContentType("text/html");
//
//        // Get email from request parameters
//        String email = request.getParameter("email");
//
//        // Validate input
//        if (email == null || email.isEmpty()) {
//            response.getWriter().write("<h3>Error: Email is required</h3>");
//            return;
//        }
//
//        try (Connection conn = connectionProvider.getCon()) {
//            // Check if email exists in the "users" table
//            String searchUserQuery = "SELECT user_id, email_verified FROM users WHERE email = ?";
//            try (PreparedStatement searchUserStmt = conn.prepareStatement(searchUserQuery)) {
//                searchUserStmt.setString(1, email);
//                ResultSet userResult = searchUserStmt.executeQuery();
//
//                if (!userResult.next()) {
//                    response.getWriter().write("<h3>Error: Email not found. Please register first.</h3>");
//                    return;
//                }
//
//                int userId = userResult.getInt("user_id");
//                boolean emailVerified = userResult.getBoolean("email_verified");
//
//                // If email is already verified, respond accordingly
//                if (emailVerified) {
//                    response.getWriter().write("<h3>Email is already verified. You can log in now.</h3>");
//                    return;
//                }
//
//                // Generate verification token
//                String token = UUID.randomUUID().toString();
//                long currentTimeMillis = System.currentTimeMillis();
//                long expiryTime = currentTimeMillis + (24 * 60 * 60 * 1000); // Token expiry: 24 hours
//                String expiresAt = new java.sql.Timestamp(expiryTime).toString();
//
//                // Insert token into `user_tokens` table
//                String insertTokenQuery = "INSERT INTO user_tokens (user_id, token, token_type, expires_at) VALUES (?, ?, 'EMAIL_VERIFY', ?)";
//                try (PreparedStatement insertTokenStmt = conn.prepareStatement(insertTokenQuery)) {
//                    insertTokenStmt.setInt(1, userId);
//                    insertTokenStmt.setString(2, token);
//                    insertTokenStmt.setString(3, expiresAt);
//                    insertTokenStmt.executeUpdate();
//                }
//
//                // Generate email verification link using centralized `APP_URL`
//                String verificationLink = AppConfiguration.APP_URL + "/verify-email?token=" + token;
//
//                // Prepare email content
//                String subject = "Verify Your Email Address";
//                String htmlContent = "<p>Dear user,<br><br>Please confirm your email address by clicking the link below:</p>"
//                        + "<a href=\"" + verificationLink + "\">Verify Email</a><br><br>"
//                        + "<p>If you did not sign up for this account, you can safely ignore this email.</p>";
//
//                // Send the verification email
//                Mail.sendMail(email, subject, htmlContent);
//                LOGGER.info("Verification email sent to: " + email);
//
//                // Respond to client
//                response.getWriter().write("<h3>A verification email has been sent to " + email + ". Please check your inbox to verify your email address.</h3>");
//            }
//        } catch (MessagingException e) {
//            LOGGER.log(Level.SEVERE, "Failed to send verification email.", e);
//            response.getWriter().write("<h3>Error: Could not send verification email. Please try again later.</h3>");
//        } catch (SQLException e) {
//            LOGGER.log(Level.SEVERE, "Database error occurred.", e);
//            response.getWriter().write("<h3>Error: Something went wrong. Please try again later.</h3>");
//        } catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "An unexpected error occurred.", e);
//            response.getWriter().write("<h3>Error: Something went wrong. Please contact support.</h3>");
//        }
//    }
//}