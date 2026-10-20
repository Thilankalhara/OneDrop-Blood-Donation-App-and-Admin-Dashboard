package controller;

import database.connectionProvider;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/UserManagementServlet")
public class UserManagementServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        try {
            // Fetch users with donation counts
            ResultSet rs = connectionProvider.executeSearch(
                "SELECT u.*, " +
                "COALESCE((SELECT COUNT(*) FROM donations d WHERE d.user_id = u.user_id AND d.verified = TRUE), 0) AS donation_count " +
                "FROM users u " +
                "ORDER BY u.created_at DESC"
            );

            StringBuilder json = new StringBuilder();
            json.append("[");

            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                int userId = rs.getInt("user_id");
                String name = rs.getString("name");
                String email = rs.getString("email");
                String mobile = rs.getString("mobile");
                String bloodType = rs.getString("blood_type");
                String gender = rs.getString("gender");
                String status = rs.getString("status");
                String profileImage = rs.getString("profile_image");
                if (profileImage == null || profileImage.isEmpty()) {
                    profileImage = "https://via.placeholder.com/120";
                }
                String createdAt = rs.getString("created_at");
                int donationCount = rs.getInt("donation_count");

                json.append(String.format(
                    "{\"user_id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"mobile\":\"%s\",\"blood_type\":\"%s\",\"gender\":\"%s\",\"status\":\"%s\",\"profile_image\":\"%s\",\"created_at\":\"%s\",\"donation_count\":%d}",
                    userId, escapeJson(name), escapeJson(email), escapeJson(mobile),
                    escapeJson(bloodType), escapeJson(gender), escapeJson(status),
                    escapeJson(profileImage), escapeJson(createdAt), donationCount
                ));
            }

            json.append("]");
            out.print(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
            out.print("[]");
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        String action = request.getParameter("action");

        try {
            if ("toggleStatus".equals(action)) {
                int userId = Integer.parseInt(request.getParameter("userId"));
                String currentStatus = request.getParameter("currentStatus");
                String newStatus = "ACTIVE".equals(currentStatus) ? "INACTIVE" : "ACTIVE";

                int updated = connectionProvider.executeIUD(
                        "UPDATE users SET status='" + newStatus + "' WHERE user_id=" + userId
                );
                out.print("{\"success\":" + (updated > 0) + ",\"new_status\":\"" + newStatus + "\"}");
            } else {
                out.print("{\"success\":false,\"message\":\"Invalid action\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"success\":false,\"message\":\"Server error\"}");
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
