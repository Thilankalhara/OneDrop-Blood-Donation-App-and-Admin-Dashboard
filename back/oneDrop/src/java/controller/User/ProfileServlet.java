package controller.User;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import database.connectionProvider;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/ProfileServlet")
public class ProfileServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(ProfileServlet.class.getName());
    private static final Gson   GSON   = new Gson();

    // ── GET: fetch profile ─────────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out    = response.getWriter();
        JsonObject  result = new JsonObject();

        // ✅ Read userId from JWT attribute
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            result.addProperty("status",  "error");
            result.addProperty("message", "Unauthorized");
            out.print(result); out.flush(); return;
        }

        try {
            Connection conn = connectionProvider.getCon();

            PreparedStatement stmt = conn.prepareStatement(
                "SELECT u.user_id, u.name, u.email, u.mobile, u.gender, " +
                "u.blood_type, u.profile_image, u.created_at, " +
                "COUNT(d.donation_id) AS donation_count " +
                "FROM users u " +
                "LEFT JOIN donations d ON u.user_id = d.user_id " +
                "WHERE u.user_id = ? " +
                "GROUP BY u.user_id, u.name, u.email, u.mobile, u.gender, " +
                "u.blood_type, u.profile_image, u.created_at");
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                result.addProperty("status",         "success");
                result.addProperty("user_id",        rs.getInt("user_id"));
                result.addProperty("name",           nvl(rs.getString("name")));
                result.addProperty("email",          nvl(rs.getString("email")));
                result.addProperty("mobile",         nvl(rs.getString("mobile")));
                result.addProperty("gender",         nvl(rs.getString("gender")));
                result.addProperty("blood_type",     nvl(rs.getString("blood_type")));
                result.addProperty("created_at",     nvl(rs.getString("created_at")));
                result.addProperty("donation_count", rs.getInt("donation_count"));
                String img = nvl(rs.getString("profile_image"));
                result.addProperty("profile_image",  img.replaceAll("\\s+", ""));

                boolean complete =
                        rs.getString("mobile")     != null && !rs.getString("mobile").isEmpty() &&
                        rs.getString("gender")     != null && !rs.getString("gender").isEmpty() &&
                        rs.getString("blood_type") != null && !rs.getString("blood_type").isEmpty();
                result.addProperty("profile_complete", complete);
            } else {
                result.addProperty("status",  "error");
                result.addProperty("message", "User not found");
            }
            rs.close(); stmt.close();
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "ProfileServlet GET error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }

        out.print(result); out.flush();
    }

    // ── POST: update / change password / upload image / delete account ─────
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out    = response.getWriter();
        JsonObject  result = new JsonObject();

        // ✅ Read userId from JWT attribute
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            result.addProperty("status",  "error");
            result.addProperty("message", "Unauthorized");
            out.print(result); out.flush(); return;
        }

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }

        JsonObject json;
        try {
            json = GSON.fromJson(body.toString(), JsonObject.class);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.addProperty("status",  "error");
            result.addProperty("message", "Invalid JSON");
            out.print(result); out.flush(); return;
        }

        // ✅ Inject the verified userId into the json so helper methods can use it
        json.addProperty("user_id", userId);

        String action = json.has("action") ? json.get("action").getAsString() : "update";
        switch (action) {
            case "upload_image":    handleUploadImage(json, result, response);    break;
            case "change_password": handleChangePassword(json, result, response); break;
            case "delete_account":  handleDeleteAccount(json, result, response);  break;
            default:                handleUpdateProfile(json, result, response);
        }

        out.print(result); out.flush();
    }

    // ── Delete account ─────────────────────────────────────────────────────
    private void handleDeleteAccount(JsonObject json, JsonObject result,
                                     HttpServletResponse response) {
        int    userId   = json.get("user_id").getAsInt();
        String password = json.has("password") ? json.get("password").getAsString() : "";

        if (password.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.addProperty("status",  "error");
            result.addProperty("message", "Password is required to delete account");
            return;
        }

        try {
            Connection conn = connectionProvider.getCon();

            PreparedStatement verify = conn.prepareStatement(
                "SELECT user_id FROM users WHERE user_id = ? AND password = ?");
            verify.setInt(1, userId);
            verify.setString(2, password);
            ResultSet rs = verify.executeQuery();

            if (!rs.next()) {
                result.addProperty("status",  "wrong_password");
                result.addProperty("message", "Incorrect password.");
                rs.close(); verify.close(); return;
            }
            rs.close(); verify.close();

            PreparedStatement del = conn.prepareStatement(
                "DELETE FROM users WHERE user_id = ?");
            del.setInt(1, userId);
            del.executeUpdate();
            del.close();

            response.setStatus(HttpServletResponse.SC_OK);
            result.addProperty("status",  "success");
            result.addProperty("message", "Account deleted successfully.");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Delete account error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }
    }

    // ── Upload profile image ───────────────────────────────────────────────
    private void handleUploadImage(JsonObject json, JsonObject result,
                                   HttpServletResponse response) {
        int    userId = json.get("user_id").getAsInt();
        String image  = json.has("image") ? json.get("image").getAsString() : "";

        if (image.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.addProperty("status",  "error");
            result.addProperty("message", "image is required");
            return;
        }

        image = image.replaceAll("\\s+", "");
        try { Base64.getDecoder().decode(image); }
        catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.addProperty("status",  "error");
            result.addProperty("message", "Invalid image data");
            return;
        }

        try {
            Connection        conn = connectionProvider.getCon();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET profile_image = ? WHERE user_id = ?");
            stmt.setString(1, image);
            stmt.setInt(2, userId);
            int rows = stmt.executeUpdate();
            stmt.close();

            if (rows > 0) {
                response.setStatus(HttpServletResponse.SC_OK);
                result.addProperty("status",  "success");
                result.addProperty("message", "Profile image updated successfully");
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                result.addProperty("status",  "error");
                result.addProperty("message", "User not found");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Upload image error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }
    }

    // ── Update profile fields ──────────────────────────────────────────────
    private void handleUpdateProfile(JsonObject json, JsonObject result,
                                     HttpServletResponse response) {
        int userId = json.get("user_id").getAsInt();

        try {
            Connection        conn = connectionProvider.getCon();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET name = ?, mobile = ?, gender = ?, blood_type = ? " +
                "WHERE user_id = ?");
            stmt.setString(1, json.has("name")       ? json.get("name").getAsString()       : null);
            stmt.setString(2, json.has("mobile")     ? json.get("mobile").getAsString()     : null);
            stmt.setString(3, json.has("gender")     ? json.get("gender").getAsString()     : null);
            stmt.setString(4, json.has("blood_type") ? json.get("blood_type").getAsString() : null);
            stmt.setInt(5, userId);
            stmt.executeUpdate();
            stmt.close();

            PreparedStatement check = conn.prepareStatement(
                "SELECT mobile, gender, blood_type FROM users WHERE user_id = ?");
            check.setInt(1, userId);
            ResultSet rs = check.executeQuery();
            boolean complete = false;
            if (rs.next()) {
                complete =
                    rs.getString("mobile")     != null && !rs.getString("mobile").isEmpty() &&
                    rs.getString("gender")     != null && !rs.getString("gender").isEmpty() &&
                    rs.getString("blood_type") != null && !rs.getString("blood_type").isEmpty();
            }
            rs.close(); check.close();

            response.setStatus(HttpServletResponse.SC_OK);
            result.addProperty("status",           "success");
            result.addProperty("message",          "Profile updated successfully.");
            result.addProperty("profile_complete", complete);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Update profile error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }
    }

    // ── Change password ────────────────────────────────────────────────────
    private void handleChangePassword(JsonObject json, JsonObject result,
                                      HttpServletResponse response) {
        int    userId = json.get("user_id").getAsInt();
        String oldPw  = json.has("old_password") ? json.get("old_password").getAsString() : "";
        String newPw  = json.has("new_password") ? json.get("new_password").getAsString() : "";

        if (oldPw.isEmpty() || newPw.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.addProperty("status",  "error");
            result.addProperty("message", "old_password and new_password are required.");
            return;
        }
        if (newPw.length() < 6) {
            result.addProperty("status",  "error");
            result.addProperty("message", "New password must be at least 6 characters.");
            return;
        }

        try {
            Connection        conn   = connectionProvider.getCon();
            PreparedStatement verify = conn.prepareStatement(
                "SELECT user_id FROM users WHERE user_id = ? AND password = ?");
            verify.setInt(1, userId);
            verify.setString(2, oldPw);
            ResultSet rs = verify.executeQuery();

            if (!rs.next()) {
                result.addProperty("status",  "wrong_password");
                result.addProperty("message", "Current password is incorrect.");
                rs.close(); verify.close(); return;
            }
            rs.close(); verify.close();

            PreparedStatement update = conn.prepareStatement(
                "UPDATE users SET password = ? WHERE user_id = ?");
            update.setString(1, newPw);
            update.setInt(2, userId);
            update.executeUpdate();
            update.close();

            result.addProperty("status",  "success");
            result.addProperty("message", "Password changed successfully.");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Change password error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }
    }

    private String nvl(String s) { return s != null ? s : ""; }
}