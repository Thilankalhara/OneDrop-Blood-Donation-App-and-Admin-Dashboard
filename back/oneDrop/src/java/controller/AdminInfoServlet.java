package controller;

import database.connectionProvider;
import com.google.gson.Gson;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/AdminInfoServlet")
public class AdminInfoServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("adminId") == null) {
            out.print("{\"error\":\"Not logged in\"}");
            return;
        }

        try {
            int adminId = (int) session.getAttribute("adminId");

            ResultSet rs = connectionProvider.executeSearch(
                    "SELECT name, email FROM admins WHERE admin_id=" + adminId
            );

            Map<String, Object> adminInfo = new HashMap<>();

            if (rs.next()) {
                adminInfo.put("name", rs.getString("name"));
                adminInfo.put("email", rs.getString("email"));
                adminInfo.put("initial", rs.getString("name").substring(0, 1).toUpperCase());
            } else {
                adminInfo.put("error", "Admin not found");
            }

            out.print(gson.toJson(adminInfo));

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}