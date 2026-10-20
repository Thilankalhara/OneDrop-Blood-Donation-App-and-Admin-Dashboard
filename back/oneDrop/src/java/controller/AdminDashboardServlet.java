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

@WebServlet("/AdminDashboardServlet")
public class AdminDashboardServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        try {
            int totalUsers = 0;
            int totalDonations = 0;
            int totalQRCodes = 0;
            int totalCenters = 0;

            // Count Users
            ResultSet rs1 = connectionProvider.executeSearch("SELECT COUNT(*) AS total FROM users");
            if (rs1.next()) totalUsers = rs1.getInt("total");

            // Count Donations
            ResultSet rs2 = connectionProvider.executeSearch("SELECT COUNT(*) AS total FROM donations");
            if (rs2.next()) totalDonations = rs2.getInt("total");

            // Count QR Codes
            ResultSet rs3 = connectionProvider.executeSearch("SELECT COUNT(*) AS total FROM qr_codes");
            if (rs3.next()) totalQRCodes = rs3.getInt("total");

            // Count Donation Centers
            ResultSet rs4 = connectionProvider.executeSearch("SELECT COUNT(*) AS total FROM hospitals");
            if (rs4.next()) totalCenters = rs4.getInt("total");

            // Send JSON response
            String json = String.format(
                    "{\"totalUsers\":%d,\"totalDonations\":%d,\"totalQRCodes\":%d,\"totalCenters\":%d}",
                    totalUsers, totalDonations, totalQRCodes, totalCenters
            );
            out.print(json);

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"totalUsers\":0,\"totalDonations\":0,\"totalQRCodes\":0,\"totalCenters\":0}");
        }
    }
}
