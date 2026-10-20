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
 * HospitalServlet — GET /Hospitals
 * Returns list of blood donation hospitals.
 * Protected by JwtFilter — token is validated before this runs.
 * No userId needed here (hospitals are not user-specific).
 */
@WebServlet("/Hospitals")
public class HospitalServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(HospitalServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin",  "*");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        PrintWriter out = response.getWriter();

        // ✅ Token is already validated by JwtFilter before reaching here.
        // Hospitals are not user-specific so we don't need the userId.
        // We still verify the attribute exists as a safety check.
        if (request.getAttribute("authenticatedUserId") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"error\":\"Unauthorized\"}");
            out.flush(); return;
        }

        String statusFilter = request.getParameter("status");

        try {
            Connection        conn = connectionProvider.getCon();
            PreparedStatement stmt;

            if (statusFilter != null && !statusFilter.isEmpty()) {
                stmt = conn.prepareStatement(
                    "SELECT hospital_id, name, phone, latitude, longitude, status " +
                    "FROM hospitals WHERE status = ? ORDER BY name ASC");
                stmt.setString(1, statusFilter);
            } else {
                stmt = conn.prepareStatement(
                    "SELECT hospital_id, name, phone, latitude, longitude, status " +
                    "FROM hospitals ORDER BY status ASC, name ASC");
            }

            ResultSet  rs        = stmt.executeQuery();
            JsonArray  hospitals = new JsonArray();

            while (rs.next()) {
                JsonObject h = new JsonObject();
                h.addProperty("hospital_id", rs.getInt("hospital_id"));
                h.addProperty("name",        nvl(rs.getString("name")));
                h.addProperty("phone",       nvl(rs.getString("phone")));
                double lat = rs.getDouble("latitude");
                double lng = rs.getDouble("longitude");
                h.addProperty("latitude",    rs.wasNull() ? 0 : lat);
                h.addProperty("longitude",   rs.wasNull() ? 0 : lng);
                h.addProperty("status",      nvl(rs.getString("status")));
                hospitals.add(h);
            }
            rs.close(); stmt.close();

            response.setStatus(HttpServletResponse.SC_OK);
            out.print(hospitals);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "HospitalServlet error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("[]");
        }

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