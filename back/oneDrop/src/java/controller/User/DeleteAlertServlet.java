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

@WebServlet("/DeleteAlert")
public class DeleteAlertServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Access-Control-Allow-Origin",  "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out = null;
        try {
            out = response.getWriter();

            // ✅ Read userId from JWT attribute
            Integer userId = (Integer) request.getAttribute("authenticatedUserId");
            if (userId == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject res = new JsonObject();
                res.addProperty("status", false);
                res.addProperty("message", "Unauthorized");
                out.write(gson.toJson(res));
                return;
            }

            // ── Read JSON body ─────────────────────────────────────────────
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JsonObject json    = gson.fromJson(sb.toString(), JsonObject.class);
            int        alertId = json.has("alertId") ? json.get("alertId").getAsInt() : -1;

            if (alertId == -1) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject res = new JsonObject();
                res.addProperty("status",  false);
                res.addProperty("message", "alertId is required");
                out.write(gson.toJson(res));
                return;
            }

            try (Connection conn = connectionProvider.getCon()) {

                // ✅ Verify the alert belongs to this user (or is a global alert with NULL user_id)
                // before deleting — prevents one user deleting another user's alerts
                PreparedStatement check = conn.prepareStatement(
                    "SELECT alert_id FROM alerts " +
                    "WHERE alert_id = ? AND (user_id = ? OR user_id IS NULL)");
                check.setInt(1, alertId);
                check.setInt(2, userId);
                ResultSet rs = check.executeQuery();
                boolean owned = rs.next();
                rs.close(); check.close();

                if (!owned) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    JsonObject res = new JsonObject();
                    res.addProperty("status",  false);
                    res.addProperty("message", "Alert not found or access denied");
                    out.write(gson.toJson(res));
                    return;
                }

                PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM alerts WHERE alert_id = ?");
                stmt.setInt(1, alertId);
                int rows = stmt.executeUpdate();
                stmt.close();

                JsonObject res = new JsonObject();
                response.setStatus(HttpServletResponse.SC_OK);
                res.addProperty("status",  rows > 0);
                res.addProperty("message", rows > 0
                        ? "Alert deleted successfully"
                        : "Alert not found or already deleted");
                out.write(gson.toJson(res));
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) {
                JsonObject res = new JsonObject();
                res.addProperty("status",  false);
                res.addProperty("message", "Server error: " + e.getMessage());
                out.write(gson.toJson(res));
            }
        } finally {
            if (out != null) out.close();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) {
        res.setHeader("Access-Control-Allow-Origin",  "*");
        res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        res.setStatus(HttpServletResponse.SC_OK);
    }
}