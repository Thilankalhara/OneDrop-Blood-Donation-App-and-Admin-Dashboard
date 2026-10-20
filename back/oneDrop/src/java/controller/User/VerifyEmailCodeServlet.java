package controller.User;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import database.connectionProvider;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/VerifyEmailCode")
public class VerifyEmailCodeServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        PrintWriter out = null;

        try {
            out = response.getWriter();

            // Read JSON from request body
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JsonObject jsonRequest = gson.fromJson(sb.toString(), JsonObject.class);
            String email = jsonRequest.has("email") ? 
                jsonRequest.get("email").getAsString().trim().toLowerCase() : "";
            String code = jsonRequest.has("code") ? 
                jsonRequest.get("code").getAsString().trim() : "";

            // Validation
            if (email.isEmpty() || code.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("status", false);
                jsonResponse.addProperty("message", "Email and code are required");
                out.write(gson.toJson(jsonResponse));
                return;
            }

            if (code.length() != 6) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("status", false);
                jsonResponse.addProperty("message", "Code must be 6 digits");
                out.write(gson.toJson(jsonResponse));
                return;
            }

            try (Connection conn = connectionProvider.getCon()) {
                // Get user ID
                PreparedStatement userStmt = conn.prepareStatement(
                    "SELECT user_id, email_verified FROM users WHERE LOWER(email) = ?"
                );
                userStmt.setString(1, email);
                ResultSet rs = userStmt.executeQuery();

                if (!rs.next()) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject jsonResponse = new JsonObject();
                    jsonResponse.addProperty("status", false);
                    jsonResponse.addProperty("message", "Email not found");
                    out.write(gson.toJson(jsonResponse));
                    return;
                }

                int userId = rs.getInt("user_id");
                boolean emailVerified = rs.getBoolean("email_verified");

                if (emailVerified) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject jsonResponse = new JsonObject();
                    jsonResponse.addProperty("status", false);
                    jsonResponse.addProperty("message", "Email already verified");
                    out.write(gson.toJson(jsonResponse));
                    return;
                }

                // Verify the token
                PreparedStatement tokenStmt = conn.prepareStatement(
                    "SELECT token_id FROM user_tokens " +
                    "WHERE user_id = ? AND token = ? AND token_type = 'EMAIL_VERIFY' " +
                    "AND used = FALSE AND expires_at > NOW()"
                );
                tokenStmt.setInt(1, userId);
                tokenStmt.setString(2, code);
                ResultSet tokenRs = tokenStmt.executeQuery();

                if (!tokenRs.next()) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject jsonResponse = new JsonObject();
                    jsonResponse.addProperty("status", false);
                    jsonResponse.addProperty("message", "Invalid or expired verification code");
                    out.write(gson.toJson(jsonResponse));
                    return;
                }

                int tokenId = tokenRs.getInt("token_id");

                // Mark token as used
                PreparedStatement markUsedStmt = conn.prepareStatement(
                    "UPDATE user_tokens SET used = TRUE WHERE token_id = ?"
                );
                markUsedStmt.setInt(1, tokenId);
                markUsedStmt.executeUpdate();

                // Update user status to verified and active
                PreparedStatement updateUserStmt = conn.prepareStatement(
                    "UPDATE users SET email_verified = TRUE, status = 'ACTIVE' WHERE user_id = ?"
                );
                updateUserStmt.setInt(1, userId);
                updateUserStmt.executeUpdate();

                response.setStatus(HttpServletResponse.SC_OK);
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("status", true);
                jsonResponse.addProperty("message", "Email verified successfully");
                out.write(gson.toJson(jsonResponse));

            } catch (Exception dbEx) {
                dbEx.printStackTrace();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("status", false);
                jsonResponse.addProperty("message", "Database error: " + dbEx.getMessage());
                out.write(gson.toJson(jsonResponse));
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) {
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.addProperty("status", false);
                jsonResponse.addProperty("message", "Server error: " + e.getMessage());
                out.write(gson.toJson(jsonResponse));
            }
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}