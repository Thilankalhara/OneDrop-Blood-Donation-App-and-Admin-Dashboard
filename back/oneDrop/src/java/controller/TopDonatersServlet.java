package controller;

import database.connectionProvider;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/TopDonatersServlet")
public class TopDonatersServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out  = response.getWriter();
        Gson        gson = new Gson();

        try {
            JsonArray donatersArray = new JsonArray();

            // ✅ Only ACTIVE users, ranked by total donations DESC then points DESC
            String mainQuery =
                "SELECT u.user_id, u.name, u.email, u.blood_type, u.profile_image, " +
                "COUNT(d.donation_id)        AS total_donations, " +
                "COALESCE(SUM(d.points), 0)  AS total_points, " +
                "MAX(d.donation_date)         AS last_donation, " +
                "ROW_NUMBER() OVER (" +
                "    ORDER BY COUNT(d.donation_id) DESC, " +
                "             COALESCE(SUM(d.points), 0) DESC, " +
                "             u.user_id ASC" +
                ") AS rank_position " +
                "FROM users u " +
                "LEFT JOIN donations d ON u.user_id = d.user_id " +
                "WHERE u.status = 'ACTIVE' " +
                "GROUP BY u.user_id, u.name, u.email, u.blood_type, u.profile_image " +
                "ORDER BY rank_position ASC";

            ResultSet rs = connectionProvider.executeSearch(mainQuery);

            while (rs.next()) {
                int    uid = rs.getInt("user_id");

                JsonObject donater = new JsonObject();
                donater.addProperty("user_id",          uid);
                donater.addProperty("rank",             rs.getInt("rank_position"));
                donater.addProperty("name",             nvl(rs.getString("name")));
                donater.addProperty("email",            nvl(rs.getString("email")));
                donater.addProperty("blood_type",       nvl(rs.getString("blood_type")));
                // ✅ Strip newlines from base64 image
                String img = nvl(rs.getString("profile_image"));
                donater.addProperty("profile_image",    img.replaceAll("\\s+", ""));
                donater.addProperty("total_donations",  rs.getInt("total_donations"));
                donater.addProperty("total_points",     rs.getInt("total_points"));
                donater.addProperty("last_donation",
                        rs.getString("last_donation") != null
                                ? rs.getString("last_donation") : "");

                // ── Donation history ───────────────────────────────────────
                JsonArray historyArray = new JsonArray();
                String historyQuery =
                    "SELECT donation_date, points FROM donations " +
                    "WHERE user_id = " + uid + " " +
                    "ORDER BY donation_date DESC";

                ResultSet historyRS = connectionProvider.executeSearch(historyQuery);
                while (historyRS.next()) {
                    JsonObject donation = new JsonObject();
                    donation.addProperty("date",   historyRS.getString("donation_date"));
                    donation.addProperty("points", historyRS.getInt("points"));
                    historyArray.add(donation);
                }
                donater.add("donations", historyArray);

                donatersArray.add(donater);
            }

            out.print(gson.toJson(donatersArray));

        } catch (Exception e) {
            e.printStackTrace();
            out.print("[]");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    private String nvl(String s) { return s != null ? s : ""; }
}