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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DonationsServlet — GET /Donations
 *
 * ✅ userId is read from the JWT attribute set by JwtFilter,
 *    NOT from the ?userId= query parameter.
 *
 * Returns:
 * {
 *   "status": "success",
 *   "total_amount": 250.00,
 *   "total_count": 3,
 *   "history": [
 *     { "monetary_donation_id": 1, "amount": 100.00,
 *       "payment_status": "SUCCESS", "donated_at": "2026-03-15 10:00:00" },
 *     ...
 *   ]
 * }
 */
@WebServlet("/Donations")
public class DonationsServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(DonationsServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin",  "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out    = response.getWriter();
        JsonObject  result = new JsonObject();

        // ✅ Read userId from JWT attribute — never from ?userId= query param
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            result.addProperty("status",  "error");
            result.addProperty("message", "Unauthorized");
            out.print(result); out.flush(); return;
        }

        try {
            Connection conn = connectionProvider.getCon();

            // ── Total amount + count (SUCCESS only) ────────────────────────
            PreparedStatement totalStmt = conn.prepareStatement(
                "SELECT COUNT(*) AS total_count, " +
                "COALESCE(SUM(amount), 0) AS total_amount " +
                "FROM monetary_donations " +
                "WHERE user_id = ? AND payment_status = 'SUCCESS'");
            totalStmt.setInt(1, userId);
            ResultSet totalRs = totalStmt.executeQuery();

            double totalAmount = 0;
            int    totalCount  = 0;
            if (totalRs.next()) {
                totalAmount = totalRs.getDouble("total_amount");
                totalCount  = totalRs.getInt("total_count");
            }
            totalRs.close(); totalStmt.close();

            // ── Full history (all statuses, newest first) ──────────────────
            PreparedStatement historyStmt = conn.prepareStatement(
                "SELECT monetary_donation_id, amount, payment_status, donated_at " +
                "FROM monetary_donations " +
                "WHERE user_id = ? " +
                "ORDER BY donated_at DESC");
            historyStmt.setInt(1, userId);
            ResultSet historyRs = historyStmt.executeQuery();

            JsonArray historyArray = new JsonArray();
            while (historyRs.next()) {
                JsonObject item = new JsonObject();
                item.addProperty("monetary_donation_id", historyRs.getInt("monetary_donation_id"));
                item.addProperty("amount",               historyRs.getDouble("amount"));
                item.addProperty("payment_status",       nvl(historyRs.getString("payment_status")));
                item.addProperty("donated_at",           nvl(historyRs.getString("donated_at")));
                historyArray.add(item);
            }
            historyRs.close(); historyStmt.close();

            result.addProperty("status",       "success");
            result.addProperty("total_amount", totalAmount);
            result.addProperty("total_count",  totalCount);
            result.add("history", historyArray);
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "DonationsServlet GET error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }

        out.print(result);
        out.flush();
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) {
        res.setHeader("Access-Control-Allow-Origin",  "*");
        res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        res.setStatus(HttpServletResponse.SC_OK);
    }

    private String nvl(String s) { return s != null ? s : ""; }
}