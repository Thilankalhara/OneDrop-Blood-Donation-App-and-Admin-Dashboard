package controller.User;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import database.connectionProvider;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet("/HomeDataServlet")
public class HomeDataServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out    = response.getWriter();
        JsonObject  result = new JsonObject();

        // ✅ Read userId from JWT attribute set by JwtFilter — never from ?userId=
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            result.addProperty("status",  "error");
            result.addProperty("message", "Unauthorized");
            out.print(result); out.flush(); return;
        }

        try {
            Connection conn = connectionProvider.getCon();

            // ── 1. User profile ────────────────────────────────────────────
            PreparedStatement userStmt = conn.prepareStatement(
                "SELECT name, blood_type, profile_image, created_at " +
                "FROM users WHERE user_id = ?");
            userStmt.setInt(1, userId);
            ResultSet userRs = userStmt.executeQuery();
            if (userRs.next()) {
                result.addProperty("name",       nvl(userRs.getString("name")));
                result.addProperty("blood_type", nvl(userRs.getString("blood_type")));
                result.addProperty("created_at", nvl(userRs.getString("created_at")));
                String img = nvl(userRs.getString("profile_image"));
                result.addProperty("profile_image", img.replaceAll("\\s+", ""));
            }
            userRs.close(); userStmt.close();

            // ── 2. Donation count ──────────────────────────────────────────
            PreparedStatement donStmt = conn.prepareStatement(
                "SELECT COUNT(*) AS donation_count FROM donations WHERE user_id = ?");
            donStmt.setInt(1, userId);
            ResultSet donRs = donStmt.executeQuery();
            int donationCount = 0;
            if (donRs.next()) donationCount = donRs.getInt("donation_count");
            result.addProperty("donation_count", donationCount);
            result.addProperty("lives_saved",    donationCount * 3);
            donRs.close(); donStmt.close();

            // ── 3. Alerts ──────────────────────────────────────────────────
            PreparedStatement alertStmt = conn.prepareStatement(
                "SELECT alert_id, title, message, created_at " +
                "FROM alerts " +
                "WHERE user_id = ? OR user_id IS NULL " +
                "ORDER BY created_at DESC LIMIT 20");
            alertStmt.setInt(1, userId);
            ResultSet alertRs = alertStmt.executeQuery();

            JsonArray alertsArray = new JsonArray();
            int alertCount = 0;
            while (alertRs.next()) {
                JsonObject alert = new JsonObject();
                alert.addProperty("alert_id",   alertRs.getInt("alert_id"));
                alert.addProperty("title",      alertRs.getString("title"));
                alert.addProperty("message",    alertRs.getString("message"));
                alert.addProperty("created_at", alertRs.getString("created_at"));
                alert.addProperty("is_read",    false);
                alertsArray.add(alert);
                alertCount++;
            }
            result.addProperty("alert_count", alertCount);
            result.add("alerts", alertsArray);
            alertRs.close(); alertStmt.close();

            result.addProperty("status", "success");
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }

        out.print(result);
        out.flush();
    }

    private String nvl(String s) { return s != null ? s : ""; }
}