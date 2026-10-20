package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import database.connectionProvider;

@WebServlet("/MonetaryDonationsServlet")
public class MonetaryDonationsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        String action = request.getParameter("action");
        
        try {
            if ("list".equals(action)) {
                listDonations(request, response, out);
            } else if ("export".equals(action)) {
                exportDonations(request, response);
            } else {
                Map<String, String> errorMap = new HashMap<>();
                errorMap.put("error", "Invalid action");
                out.print(gson.toJson(errorMap));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("error", e.getMessage());
            out.print(gson.toJson(errorMap));
        }
    }
    
    /**
     * List monetary donations with filters
     */
    private void listDonations(HttpServletRequest request, HttpServletResponse response, 
                               PrintWriter out) throws Exception {
        
        String period = request.getParameter("period");
        String status = request.getParameter("status");
        String search = request.getParameter("search");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        
        System.out.println("Filters - Period: " + period + ", Status: " + status + ", Search: " + search);
        
        // Build query
        StringBuilder query = new StringBuilder();
        query.append("SELECT md.monetary_donation_id, md.amount, md.payment_status, md.donated_at, ");
        query.append("u.name AS donor_name, u.email AS donor_email ");
        query.append("FROM monetary_donations md ");
        query.append("INNER JOIN users u ON md.user_id = u.user_id ");
        query.append("WHERE 1=1 ");
        
        List<Object> params = new ArrayList<>();
        
        // Apply date filter
        if ("custom".equals(period) && startDate != null && !startDate.isEmpty() && 
            endDate != null && !endDate.isEmpty()) {
            query.append("AND DATE(md.donated_at) BETWEEN ? AND ? ");
            params.add(startDate);
            params.add(endDate);
        } else if (period != null && !period.isEmpty() && !"all".equals(period)) {
            query.append("AND ");
            switch (period) {
                case "today":
                    query.append("DATE(md.donated_at) = CURDATE() ");
                    break;
                case "week":
                    query.append("YEARWEEK(md.donated_at, 1) = YEARWEEK(CURDATE(), 1) ");
                    break;
                case "month":
                    query.append("YEAR(md.donated_at) = YEAR(CURDATE()) AND MONTH(md.donated_at) = MONTH(CURDATE()) ");
                    break;
                case "year":
                    query.append("YEAR(md.donated_at) = YEAR(CURDATE()) ");
                    break;
            }
        }
        
        // Apply status filter
        if (status != null && !status.isEmpty()) {
            query.append("AND md.payment_status = ? ");
            params.add(status);
        }
        
        // Apply search filter
        if (search != null && !search.trim().isEmpty()) {
            query.append("AND (u.name LIKE ? OR u.email LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }
        
        query.append("ORDER BY md.donated_at DESC LIMIT 500");
        
        System.out.println("Executing query: " + query.toString());
        
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(query.toString());
        
        // Set parameters
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        
        ResultSet rs = ps.executeQuery();
        
        List<Map<String, Object>> donations = new ArrayList<>();
        
        while (rs.next()) {
            Map<String, Object> donation = new HashMap<>();
            donation.put("id", rs.getInt("monetary_donation_id"));
            donation.put("amount", rs.getDouble("amount"));
            donation.put("status", rs.getString("payment_status"));
            donation.put("donated_at", rs.getTimestamp("donated_at").toString());
            donation.put("donor_name", rs.getString("donor_name"));
            donation.put("donor_email", rs.getString("donor_email"));
            donations.add(donation);
        }
        
        rs.close();
        ps.close();
        
        // Get statistics
        Map<String, Object> stats = calculateStatistics(con, period, status, search, 
                                                        startDate, endDate, params);
        
        // Build response
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("donations", donations);
        responseData.put("stats", stats);
        
        out.print(gson.toJson(responseData));
    }
    
    /**
     * Calculate statistics
     */
    private Map<String, Object> calculateStatistics(Connection con, String period, 
            String status, String search, String startDate, String endDate, 
            List<Object> params) throws Exception {
        
        StringBuilder statsQuery = new StringBuilder();
        statsQuery.append("SELECT ");
        statsQuery.append("COUNT(md.monetary_donation_id) AS total_count, ");
        statsQuery.append("COALESCE(SUM(md.amount), 0) AS total_amount, ");
        statsQuery.append("COALESCE(AVG(md.amount), 0) AS avg_donation, ");
        statsQuery.append("COUNT(DISTINCT md.user_id) AS total_donors ");
        statsQuery.append("FROM monetary_donations md ");
        statsQuery.append("INNER JOIN users u ON md.user_id = u.user_id ");
        statsQuery.append("WHERE 1=1 ");
        
        // Apply same filters
        if ("custom".equals(period) && startDate != null && !startDate.isEmpty() && 
            endDate != null && !endDate.isEmpty()) {
            statsQuery.append("AND DATE(md.donated_at) BETWEEN ? AND ? ");
        } else if (period != null && !period.isEmpty() && !"all".equals(period)) {
            statsQuery.append("AND ");
            switch (period) {
                case "today":
                    statsQuery.append("DATE(md.donated_at) = CURDATE() ");
                    break;
                case "week":
                    statsQuery.append("YEARWEEK(md.donated_at, 1) = YEARWEEK(CURDATE(), 1) ");
                    break;
                case "month":
                    statsQuery.append("YEAR(md.donated_at) = YEAR(CURDATE()) AND MONTH(md.donated_at) = MONTH(CURDATE()) ");
                    break;
                case "year":
                    statsQuery.append("YEAR(md.donated_at) = YEAR(CURDATE()) ");
                    break;
            }
        }
        
        if (status != null && !status.isEmpty()) {
            statsQuery.append("AND md.payment_status = ? ");
        }
        
        if (search != null && !search.trim().isEmpty()) {
            statsQuery.append("AND (u.name LIKE ? OR u.email LIKE ?) ");
        }
        
        PreparedStatement statsPs = con.prepareStatement(statsQuery.toString());
        
        // Set same parameters
        for (int i = 0; i < params.size(); i++) {
            statsPs.setObject(i + 1, params.get(i));
        }
        
        ResultSet statsRs = statsPs.executeQuery();
        
        Map<String, Object> stats = new HashMap<>();
        
        if (statsRs.next()) {
            stats.put("totalCount", statsRs.getInt("total_count"));
            stats.put("totalAmount", statsRs.getDouble("total_amount"));
            stats.put("avgDonation", statsRs.getDouble("avg_donation"));
            stats.put("totalDonors", statsRs.getInt("total_donors"));
        } else {
            stats.put("totalCount", 0);
            stats.put("totalAmount", 0.0);
            stats.put("avgDonation", 0.0);
            stats.put("totalDonors", 0);
        }
        
        statsRs.close();
        statsPs.close();
        
        return stats;
    }
    
    /**
     * Export donations to Excel or PDF
     */
    private void exportDonations(HttpServletRequest request, HttpServletResponse response) 
            throws Exception {
        
        String format = request.getParameter("format");
        
        if ("excel".equals(format)) {
            exportToExcel(request, response);
        } else if ("pdf".equals(format)) {
            exportToPDF(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid export format");
        }
    }
    
    /**
     * Export to Excel (CSV format)
     */
    private void exportToExcel(HttpServletRequest request, HttpServletResponse response) 
            throws Exception {
        
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"donations_" + 
                          System.currentTimeMillis() + ".csv\"");
        
        PrintWriter writer = response.getWriter();
        
        // Write CSV header
        writer.println("ID,Donor Name,Email,Amount,Status,Donation Date");
        
        // Get donations (reuse same filter logic)
        String period = request.getParameter("period");
        String status = request.getParameter("status");
        String search = request.getParameter("search");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        
        StringBuilder query = new StringBuilder();
        query.append("SELECT md.monetary_donation_id, md.amount, md.payment_status, md.donated_at, ");
        query.append("u.name AS donor_name, u.email AS donor_email ");
        query.append("FROM monetary_donations md ");
        query.append("INNER JOIN users u ON md.user_id = u.user_id ");
        query.append("WHERE 1=1 ");
        
        List<Object> params = new ArrayList<>();
        
        // Apply filters (same as list method)
        if ("custom".equals(period) && startDate != null && !startDate.isEmpty() && 
            endDate != null && !endDate.isEmpty()) {
            query.append("AND DATE(md.donated_at) BETWEEN ? AND ? ");
            params.add(startDate);
            params.add(endDate);
        } else if (period != null && !period.isEmpty() && !"all".equals(period)) {
            query.append("AND ");
            switch (period) {
                case "today":
                    query.append("DATE(md.donated_at) = CURDATE() ");
                    break;
                case "week":
                    query.append("YEARWEEK(md.donated_at, 1) = YEARWEEK(CURDATE(), 1) ");
                    break;
                case "month":
                    query.append("YEAR(md.donated_at) = YEAR(CURDATE()) AND MONTH(md.donated_at) = MONTH(CURDATE()) ");
                    break;
                case "year":
                    query.append("YEAR(md.donated_at) = YEAR(CURDATE()) ");
                    break;
            }
        }
        
        if (status != null && !status.isEmpty()) {
            query.append("AND md.payment_status = ? ");
            params.add(status);
        }
        
        if (search != null && !search.trim().isEmpty()) {
            query.append("AND (u.name LIKE ? OR u.email LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }
        
        query.append("ORDER BY md.donated_at DESC");
        
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(query.toString());
        
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        
        ResultSet rs = ps.executeQuery();
        
        while (rs.next()) {
            writer.printf("%d,\"%s\",\"%s\",%.2f,%s,%s%n",
                rs.getInt("monetary_donation_id"),
                rs.getString("donor_name").replace("\"", "\"\""),
                rs.getString("donor_email"),
                rs.getDouble("amount"),
                rs.getString("payment_status"),
                rs.getTimestamp("donated_at").toString()
            );
        }
        
        rs.close();
        ps.close();
        writer.flush();
    }
    
    /**
     * Export to PDF (Simple text format - you can enhance with libraries like iText)
     */
    private void exportToPDF(HttpServletRequest request, HttpServletResponse response) 
            throws Exception {
        
        // For now, export as text format
        // In production, use libraries like Apache PDFBox or iText
        response.setContentType("text/plain");
        response.setHeader("Content-Disposition", "attachment; filename=\"donations_" + 
                          System.currentTimeMillis() + ".txt\"");
        
        PrintWriter writer = response.getWriter();
        
        writer.println("========================================");
        writer.println("       MONETARY DONATIONS REPORT        ");
        writer.println("========================================");
        writer.println();
        writer.println("Generated: " + new java.util.Date());
        writer.println();
        writer.println("----------------------------------------");
        writer.println();
        
        // Same query as Excel
        String period = request.getParameter("period");
        String status = request.getParameter("status");
        String search = request.getParameter("search");
        String startDate = request.getParameter("startDate");
        String endDate = request.getParameter("endDate");
        
        StringBuilder query = new StringBuilder();
        query.append("SELECT md.monetary_donation_id, md.amount, md.payment_status, md.donated_at, ");
        query.append("u.name AS donor_name, u.email AS donor_email ");
        query.append("FROM monetary_donations md ");
        query.append("INNER JOIN users u ON md.user_id = u.user_id ");
        query.append("WHERE 1=1 ");
        
        List<Object> params = new ArrayList<>();
        
        if ("custom".equals(period) && startDate != null && !startDate.isEmpty() && 
            endDate != null && !endDate.isEmpty()) {
            query.append("AND DATE(md.donated_at) BETWEEN ? AND ? ");
            params.add(startDate);
            params.add(endDate);
        } else if (period != null && !period.isEmpty() && !"all".equals(period)) {
            query.append("AND ");
            switch (period) {
                case "today":
                    query.append("DATE(md.donated_at) = CURDATE() ");
                    break;
                case "week":
                    query.append("YEARWEEK(md.donated_at, 1) = YEARWEEK(CURDATE(), 1) ");
                    break;
                case "month":
                    query.append("YEAR(md.donated_at) = YEAR(CURDATE()) AND MONTH(md.donated_at) = MONTH(CURDATE()) ");
                    break;
                case "year":
                    query.append("YEAR(md.donated_at) = YEAR(CURDATE()) ");
                    break;
            }
        }
        
        if (status != null && !status.isEmpty()) {
            query.append("AND md.payment_status = ? ");
            params.add(status);
        }
        
        if (search != null && !search.trim().isEmpty()) {
            query.append("AND (u.name LIKE ? OR u.email LIKE ?) ");
            String searchPattern = "%" + search.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }
        
        query.append("ORDER BY md.donated_at DESC");
        
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(query.toString());
        
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        
        ResultSet rs = ps.executeQuery();
        
        while (rs.next()) {
            writer.println("ID: " + rs.getInt("monetary_donation_id"));
            writer.println("Donor: " + rs.getString("donor_name"));
            writer.println("Email: " + rs.getString("donor_email"));
            writer.println("Amount: $" + String.format("%.2f", rs.getDouble("amount")));
            writer.println("Status: " + rs.getString("payment_status"));
            writer.println("Date: " + rs.getTimestamp("donated_at"));
            writer.println("----------------------------------------");
        }
        
        rs.close();
        ps.close();
        writer.flush();
    }
}