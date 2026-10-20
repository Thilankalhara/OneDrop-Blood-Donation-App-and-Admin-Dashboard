package controller.User;

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
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/ScanQRServlet")
public class ScanQRServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(ScanQRServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out    = response.getWriter();
        JsonObject  result = new JsonObject();

        // ✅ Read userId from JWT attribute — ignore any user_id in the body
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            result.addProperty("status",  "error");
            result.addProperty("message", "Unauthorized");
            out.print(result); out.flush(); return;
        }

        // ── Parse request body for qr_code only ───────────────────────────
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }

        String qrCode = null;
        try {
            com.google.gson.JsonObject req =
                    new com.google.gson.Gson().fromJson(
                            body.toString(), com.google.gson.JsonObject.class);
            qrCode = req.get("qr_code").getAsString().trim();
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.addProperty("status",  "error");
            result.addProperty("message", "Invalid request. qr_code is required.");
            out.print(result); out.flush(); return;
        }

        if (qrCode == null || qrCode.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.addProperty("status",  "error");
            result.addProperty("message", "qr_code is required.");
            out.print(result); out.flush(); return;
        }

        try {
            Connection conn = connectionProvider.getCon();

            // ── Step 1: Find QR code ───────────────────────────────────────
            PreparedStatement findStmt = conn.prepareStatement(
                "SELECT qr_id, status FROM qr_codes WHERE qr_code = ?");
            findStmt.setString(1, qrCode);
            ResultSet findRs = findStmt.executeQuery();

            if (!findRs.next()) {
                response.setStatus(HttpServletResponse.SC_OK);
                result.addProperty("status",  "invalid");
                result.addProperty("message", "Invalid QR code. Not recognized.");
                logScan(conn, -1, userId, "FAILED");
                findRs.close(); findStmt.close();
                out.print(result); out.flush(); return;
            }

            int    qrId     = findRs.getInt("qr_id");
            String qrStatus = findRs.getString("status");
            findRs.close(); findStmt.close();

            // ── Step 2: Check QR status ────────────────────────────────────
            if ("Deactivated".equals(qrStatus)) {
                response.setStatus(HttpServletResponse.SC_OK);
                result.addProperty("status",  "deactivated");
                result.addProperty("message", "This QR code has been deactivated.");
                logScan(conn, qrId, userId, "FAILED");
                out.print(result); out.flush(); return;
            }

            if ("UNUSED".equals(qrStatus)) {
                response.setStatus(HttpServletResponse.SC_OK);
                result.addProperty("status",  "unused");
                result.addProperty("message", "This QR code is not yet active.");
                logScan(conn, qrId, userId, "FAILED");
                out.print(result); out.flush(); return;
            }

            // ── Step 3: Check if already verified by this user ─────────────
            PreparedStatement alreadyStmt = conn.prepareStatement(
                "SELECT donation_id FROM donations " +
                "WHERE qr_id = ? AND user_id = ? AND verified = 1");
            alreadyStmt.setInt(1, qrId);
            alreadyStmt.setInt(2, userId);
            ResultSet alreadyRs = alreadyStmt.executeQuery();

            if (alreadyRs.next()) {
                response.setStatus(HttpServletResponse.SC_OK);
                result.addProperty("status",  "already_verified");
                result.addProperty("message", "You have already verified this donation.");
                logScan(conn, qrId, userId, "FAILED");
                alreadyRs.close(); alreadyStmt.close();
                out.print(result); out.flush(); return;
            }
            alreadyRs.close(); alreadyStmt.close();

            // ── Step 4: Create / update donation row ───────────────────────
            PreparedStatement existingStmt = conn.prepareStatement(
                "SELECT donation_id FROM donations " +
                "WHERE qr_id = ? ORDER BY donation_id DESC LIMIT 1");
            existingStmt.setInt(1, qrId);
            ResultSet existingRs = existingStmt.executeQuery();

            int     donationId  = -1;
            boolean hasExisting = existingRs.next();
            if (hasExisting) donationId = existingRs.getInt("donation_id");
            existingRs.close(); existingStmt.close();

            if (hasExisting) {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE donations SET verified = 1, user_id = ?, " +
                    "donation_date = CURDATE() WHERE donation_id = ?");
                upd.setInt(1, userId);
                upd.setInt(2, donationId);
                upd.executeUpdate();
                upd.close();
            } else {
                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO donations (user_id, qr_id, donation_date, points, verified) " +
                    "VALUES (?, ?, CURDATE(), 100, 1)",
                    Statement.RETURN_GENERATED_KEYS);
                ins.setInt(1, userId);
                ins.setInt(2, qrId);
                ins.executeUpdate();
                ResultSet keys = ins.getGeneratedKeys();
                if (keys.next()) donationId = keys.getInt(1);
                keys.close(); ins.close();
            }

            logScan(conn, qrId, userId, "SUCCESS");

            // ── Get updated totals ─────────────────────────────────────────
            PreparedStatement countStmt = conn.prepareStatement(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(points), 0) AS total_pts " +
                "FROM donations WHERE user_id = ? AND verified = 1");
            countStmt.setInt(1, userId);
            ResultSet countRs = countStmt.executeQuery();
            int donationCount = 0, totalPoints = 0;
            if (countRs.next()) {
                donationCount = countRs.getInt("cnt");
                totalPoints   = countRs.getInt("total_pts");
            }
            countRs.close(); countStmt.close();

            response.setStatus(HttpServletResponse.SC_OK);
            result.addProperty("status",         "success");
            result.addProperty("message",        "Donation verified successfully!");
            result.addProperty("points_earned",  100);
            result.addProperty("total_points",   totalPoints);
            result.addProperty("donation_count", donationCount);
            result.addProperty("donation_id",    donationId);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB error during QR scan", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", "Server error. Please try again.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during QR scan", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", "Unexpected error occurred.");
        }

        out.print(result);
        out.flush();
    }

    private void logScan(Connection conn, int qrId, int userId, String status) {
        if (qrId == -1) return;
        try {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO qr_scan_history (qr_id, user_id, scan_status) VALUES (?, ?, ?)");
            stmt.setInt(1,    qrId);
            stmt.setInt(2,    userId);
            stmt.setString(3, status);
            stmt.executeUpdate();
            stmt.close();
        } catch (Exception e) {
            LOGGER.warning("Failed to log scan: " + e.getMessage());
        }
    }
}