package model;

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
 * SessionCheckServlet — GET /SessionCheckServlet
 * package: model
 *
 * Called on app startup to verify the user's account is still active in the DB.
 * JwtFilter validates the JWT token BEFORE this runs, so userId is already trusted.
 *
 * Safe rule:
 *   valid=false ONLY for definitive DB responses (deleted/deactivated account).
 *   valid=true on ANY error so the app never logs out due to server problems.
 *
 * Returns: { "valid": true/false, "reason": "..." }
 */
@WebServlet("/SessionCheckServlet")
public class SessionCheckServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(SessionCheckServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin",  "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out    = response.getWriter();
        JsonObject  result = new JsonObject();

        // ✅ userId from JWT attribute — set by JwtFilter before this runs
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            // Should never reach here (JwtFilter would have blocked it),
            // but if it does, allow through rather than forcing a logout.
            LOGGER.warning("SessionCheckServlet: no authenticatedUserId attribute");
            result.addProperty("valid",  true);
            result.addProperty("reason", "no_attribute_allow");
            out.print(result); out.flush();
            return;
        }

        LOGGER.info("SessionCheck for userId=" + userId);

        try {
            Connection        conn = connectionProvider.getCon();
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT status FROM users WHERE user_id = ?");
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                // Account was deleted
                LOGGER.info("SessionCheck: user_not_found userId=" + userId);
                result.addProperty("valid",  false);
                result.addProperty("reason", "user_not_found");
            } else {
                String status = rs.getString("status");
                if ("ACTIVE".equals(status)) {
                    result.addProperty("valid",  true);
                    result.addProperty("reason", "ok");
                } else {
                    // Admin deactivated the account
                    LOGGER.info("SessionCheck: account_inactive userId=" + userId);
                    result.addProperty("valid",  false);
                    result.addProperty("reason", "account_inactive");
                }
            }
            rs.close(); stmt.close();
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            // ✅ DB or server error — return valid=true so app never logs out
            // the user because of a temporary server issue
            LOGGER.log(Level.SEVERE, "SessionCheckServlet error", e);
            result.addProperty("valid",  true);
            result.addProperty("reason", "server_error_allow");
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
}