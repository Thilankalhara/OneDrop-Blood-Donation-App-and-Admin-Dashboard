package controller;

import com.google.gson.Gson;
import database.connectionProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

@WebServlet("/QRHistoryServlet")
public class QRHistoryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        Gson gson = new Gson();

        ArrayList<HashMap<String, Object>> historyList = new ArrayList<>();

        try {

            String query =
                "SELECT h.scan_id, u.name, u.email, q.qr_code, h.scan_status, h.scanned_at " +
                "FROM qr_scan_history h " +
                "INNER JOIN users u ON h.user_id = u.user_id " +
                "INNER JOIN qr_codes q ON h.qr_id = q.qr_id " +
                "ORDER BY h.scanned_at DESC";

            ResultSet rs = connectionProvider.executeSearch(query);

            while (rs.next()) {

                HashMap<String, Object> row = new HashMap<>();
                row.put("scanId", rs.getInt("scan_id"));
                row.put("userName", rs.getString("name"));
                row.put("email", rs.getString("email"));
                row.put("qrCode", rs.getString("qr_code"));
                row.put("scanStatus", rs.getString("scan_status"));
                row.put("scannedAt", rs.getTimestamp("scanned_at").toString());

                historyList.add(row);
            }

            out.print(gson.toJson(historyList));

        } catch (Exception e) {
            e.printStackTrace();

            HashMap<String, String> error = new HashMap<>();
            error.put("error", "Failed to load QR scan history");

            out.print(gson.toJson(error));
        }
    }
}
