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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/LeaderboardServlet")
public class LeaderboardServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LeaderboardServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");

        PrintWriter out    = response.getWriter();
        JsonObject  result = new JsonObject();

        int userId = -1;
        try {
            String p = request.getParameter("userId");
            if (p != null && !p.isEmpty()) userId = Integer.parseInt(p);
        } catch (NumberFormatException ignored) {}

        try {
            Connection conn = connectionProvider.getCon();

            // ✅ Matches admin panel: only ACTIVE users, ordered by points DESC
            String rankSql =
                "SELECT " +
                "    user_id, " +
                "    name, " +
                "    donation_count, " +
                "    total_points, " +
                "    ROW_NUMBER() OVER (" +
                "        ORDER BY donation_count DESC, total_points DESC, user_id ASC" +
                "    ) AS rank_position " +
                "FROM ( " +
                "    SELECT " +
                "        u.user_id, " +
                "        u.name, " +
                "        COUNT(d.donation_id)       AS donation_count, " +
                "        COALESCE(SUM(d.points), 0) AS total_points " +
                "    FROM users u " +
                "    LEFT JOIN donations d ON u.user_id = d.user_id " +
                "    WHERE u.status = 'ACTIVE' " +
                "    GROUP BY u.user_id, u.name " +
                ") ranked_data " +
                "ORDER BY rank_position ASC";

            PreparedStatement stmt = conn.prepareStatement(rankSql);
            ResultSet         rs   = stmt.executeQuery();

            List<JsonObject> allList = new ArrayList<>();

            int    myRank   = 0;
            int    myPoints = 0;
            int    myCount  = 0;
            String myName   = "";

            while (rs.next()) {
                int    uid    = rs.getInt("user_id");
                String name   = rs.getString("name");
                int    count  = rs.getInt("donation_count");
                int    points = rs.getInt("total_points");
                int    rank   = rs.getInt("rank_position");

                JsonObject donor = new JsonObject();
                donor.addProperty("user_id",        uid);
                donor.addProperty("name",           name);
                donor.addProperty("initials",       getInitials(name));
                donor.addProperty("donation_count", count);
                donor.addProperty("total_points",   points);
                donor.addProperty("rank",           rank);

                allList.add(donor);

                if (uid == userId) {
                    myRank   = rank;
                    myPoints = points;
                    myCount  = count;
                    myName   = name;
                }
            }
            rs.close();
            stmt.close();

            // top3 = positions 1,2,3 | allDonors = position 4+
            JsonArray top3      = new JsonArray();
            JsonArray allDonors = new JsonArray();

            for (JsonObject donor : allList) {
                if (donor.get("rank").getAsInt() <= 3) top3.add(donor);
                else allDonors.add(donor);
            }

            // ── Hint ──────────────────────────────────────────────────────
            String hint = "";
            if (myRank == 1) {
                hint = "You're #1! Keep it up! 🏆";
            } else if (myRank > 1) {
                JsonObject above = null;
                for (JsonObject d : allList) {
                    if (d.get("rank").getAsInt() == myRank - 1) {
                        above = d; break;
                    }
                }
                if (above != null) {
                    int targetCount     = above.get("donation_count").getAsInt();
                    int donationsNeeded = Math.max(0, targetCount - myCount + 1);
                    hint = donationsNeeded > 0
                            ? donationsNeeded + " more donation" +
                              (donationsNeeded == 1 ? "" : "s") +
                              " to reach #" + (myRank - 1) + "!"
                            : "Almost there! Keep going!";
                }
            }

            JsonObject myRankObj = new JsonObject();
            myRankObj.addProperty("rank",           myRank);
            myRankObj.addProperty("name",           myName);
            myRankObj.addProperty("initials",       getInitials(myName));
            myRankObj.addProperty("total_points",   myPoints);
            myRankObj.addProperty("donation_count", myCount);
            myRankObj.addProperty("hint",           hint);

            result.addProperty("status", "success");
            result.add("top3",       top3);
            result.add("all_donors", allDonors);
            result.add("my_rank",    myRankObj);
            response.setStatus(HttpServletResponse.SC_OK);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Leaderboard error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.addProperty("status",  "error");
            result.addProperty("message", e.getMessage());
        }

        out.print(result);
        out.flush();
    }

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }
}