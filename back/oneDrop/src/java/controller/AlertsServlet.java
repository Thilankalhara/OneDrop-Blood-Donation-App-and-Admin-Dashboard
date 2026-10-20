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
import javax.servlet.http.HttpSession;

import com.google.gson.Gson;
import database.connectionProvider;

@WebServlet("/AlertsServlet")
public class AlertsServlet extends HttpServlet {
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
                listAlerts(request, response, out);
            } else if ("delete".equals(action)) {
                deleteAlert(request, response, out);
            } else if ("getAdmins".equals(action)) {
                getAdmins(request, response, out);
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        System.out.println("=================================");
        System.out.println("AlertsServlet - doPost called");
        
        HttpSession session = request.getSession(false);
        if (session == null) {
            System.out.println("ERROR: No session found");
            out.print("error: No session found. Please login again.");
            return;
        }
        
        System.out.println("Session ID: " + session.getId());
        System.out.println("Session attributes:");
        java.util.Enumeration<String> attrs = session.getAttributeNames();
        while (attrs.hasMoreElements()) {
            String attr = attrs.nextElement();
            System.out.println("  - " + attr + " = " + session.getAttribute(attr));
        }
        
        Object adminIdObj = session.getAttribute("adminId");
        if (adminIdObj == null) {
            System.out.println("ERROR: adminId not in session");
            System.out.println("Available attributes: " + java.util.Collections.list(session.getAttributeNames()));
            out.print("error: Admin not logged in. Please login again.");
            return;
        }
        
        int adminId = (Integer) adminIdObj;
        System.out.println("Admin ID from session: " + adminId);
        
        String title = request.getParameter("title");
        String message = request.getParameter("message");
        String type = request.getParameter("type");
        String email = request.getParameter("email");
        String bloodType = request.getParameter("blood");
        
        System.out.println("Alert Type: " + type);
        System.out.println("Title: " + title);
        System.out.println("Message: " + message);
        
        if (title == null || title.trim().isEmpty()) {
            out.print("error: Title is required");
            return;
        }
        
        if (message == null || message.trim().isEmpty()) {
            out.print("error: Message is required");
            return;
        }
        
        try {
            boolean success = false;
            
            if ("all".equals(type)) {
                success = sendAlertToAll(adminId, title, message);
                System.out.println("Send to all result: " + success);
            } else if ("email".equals(type)) {
                success = sendAlertToEmail(adminId, title, message, email);
                System.out.println("Send to email result: " + success);
            } else if ("bloodType".equals(type)) {
                success = sendAlertToBloodType(adminId, title, message, bloodType);
                System.out.println("Send to blood type result: " + success);
            } else {
                out.print("error: Invalid alert type");
                return;
            }
            
            if (success) {
                out.print("success");
            } else {
                out.print("error: No recipients found or database error");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            out.print("error: " + e.getMessage());
        }
    }
    
    /**
     * List all alerts with optional admin filter
     */
    private void listAlerts(HttpServletRequest request, HttpServletResponse response, 
                           PrintWriter out) throws Exception {
        
        String adminFilter = request.getParameter("admin");
        List<Map<String, Object>> alerts = new ArrayList<>();
        
        String query = "SELECT a.alert_id, a.title, a.message, a.created_at, " +
                      "ad.name AS admin_name, ad.email AS admin_email, " +
                      "u.email AS user_email, u.name AS user_name " +
                      "FROM alerts a " +
                      "INNER JOIN admins ad ON a.admins_admin_id = ad.admin_id " +
                      "LEFT JOIN users u ON a.user_id = u.user_id ";
        
        if (adminFilter != null && !adminFilter.trim().isEmpty()) {
            query += "WHERE ad.name LIKE ? ";
        }
        
        query += "ORDER BY a.created_at DESC LIMIT 100";
        
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(query);
        
        if (adminFilter != null && !adminFilter.trim().isEmpty()) {
            ps.setString(1, "%" + adminFilter + "%");
        }
        
        ResultSet rs = ps.executeQuery();
        
        while (rs.next()) {
            Map<String, Object> alert = new HashMap<>();
            alert.put("id", rs.getInt("alert_id"));
            alert.put("admin", rs.getString("admin_name"));
            alert.put("admin_email", rs.getString("admin_email"));
            
            String userEmail = rs.getString("user_email");
            alert.put("user_email", userEmail != null ? userEmail : "All Users");
            alert.put("user_name", rs.getString("user_name"));
            alert.put("title", rs.getString("title"));
            alert.put("message", rs.getString("message"));
            alert.put("created_at", rs.getTimestamp("created_at").toString());
            alerts.add(alert);
        }
        
        rs.close();
        ps.close();
        
        out.print(gson.toJson(alerts));
    }
    
    /**
     * Delete an alert
     */
    private void deleteAlert(HttpServletRequest request, HttpServletResponse response, 
                            PrintWriter out) throws Exception {
        
        String idParam = request.getParameter("id");
        if (idParam == null || idParam.isEmpty()) {
            out.print("error");
            return;
        }
        
        int alertId = Integer.parseInt(idParam);
        
        String query = "DELETE FROM alerts WHERE alert_id = ?";
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(query);
        ps.setInt(1, alertId);
        
        int result = ps.executeUpdate();
        ps.close();
        
        out.print(result > 0 ? "success" : "error");
    }
    
    /**
     * Get all admins for dropdown
     */
    private void getAdmins(HttpServletRequest request, HttpServletResponse response, 
                          PrintWriter out) throws Exception {
        
        List<Map<String, Object>> admins = new ArrayList<>();
        
        String query = "SELECT admin_id, name, email FROM admins ORDER BY name";
        
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(query);
        ResultSet rs = ps.executeQuery();
        
        while (rs.next()) {
            Map<String, Object> admin = new HashMap<>();
            admin.put("id", rs.getInt("admin_id"));
            admin.put("name", rs.getString("name"));
            admin.put("email", rs.getString("email"));
            admins.add(admin);
        }
        
        rs.close();
        ps.close();
        
        out.print(gson.toJson(admins));
    }
    
    /**
     * Send alert to all users
     */
    private boolean sendAlertToAll(int adminId, String title, String message) throws Exception {
        String getUsersQuery = "SELECT user_id FROM users WHERE status = 'ACTIVE'";
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(getUsersQuery);
        ResultSet rs = ps.executeQuery();
        
        String insertQuery = "INSERT INTO alerts (title, message, user_id, admins_admin_id) VALUES (?, ?, ?, ?)";
        PreparedStatement insertPs = con.prepareStatement(insertQuery);
        
        int count = 0;
        while (rs.next()) {
            insertPs.setString(1, title);
            insertPs.setString(2, message);
            insertPs.setInt(3, rs.getInt("user_id"));
            insertPs.setInt(4, adminId);
            insertPs.addBatch();
            count++;
            
            if (count % 100 == 0) {
                insertPs.executeBatch();
            }
        }
        
        if (count % 100 != 0) {
            insertPs.executeBatch();
        }
        
        rs.close();
        ps.close();
        insertPs.close();
        
        return count > 0;
    }
    
    /**
     * Send alert to specific user by email
     */
    private boolean sendAlertToEmail(int adminId, String title, String message, String email) 
            throws Exception {
        
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        
        // First get user_id from email
        String getUserQuery = "SELECT user_id FROM users WHERE email = ? AND status = 'ACTIVE'";
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(getUserQuery);
        ps.setString(1, email.trim());
        ResultSet rs = ps.executeQuery();
        
        if (!rs.next()) {
            rs.close();
            ps.close();
            return false;
        }
        
        int userId = rs.getInt("user_id");
        rs.close();
        ps.close();
        
        // Insert alert
        String insertQuery = "INSERT INTO alerts (title, message, user_id, admins_admin_id) VALUES (?, ?, ?, ?)";
        PreparedStatement insertPs = con.prepareStatement(insertQuery);
        insertPs.setString(1, title);
        insertPs.setString(2, message);
        insertPs.setInt(3, userId);
        insertPs.setInt(4, adminId);
        
        int result = insertPs.executeUpdate();
        insertPs.close();
        
        return result > 0;
    }
    
    /**
     * Send alert to users by blood type
     */
    private boolean sendAlertToBloodType(int adminId, String title, String message, String bloodType) 
            throws Exception {
        
        if (bloodType == null || bloodType.trim().isEmpty()) {
            return false;
        }
        
        String getUsersQuery = "SELECT user_id FROM users WHERE blood_type = ? AND status = 'ACTIVE'";
        Connection con = connectionProvider.getCon();
        PreparedStatement ps = con.prepareStatement(getUsersQuery);
        ps.setString(1, bloodType);
        ResultSet rs = ps.executeQuery();
        
        String insertQuery = "INSERT INTO alerts (title, message, user_id, admins_admin_id) VALUES (?, ?, ?, ?)";
        PreparedStatement insertPs = con.prepareStatement(insertQuery);
        
        int count = 0;
        while (rs.next()) {
            insertPs.setString(1, title);
            insertPs.setString(2, message);
            insertPs.setInt(3, rs.getInt("user_id"));
            insertPs.setInt(4, adminId);
            insertPs.addBatch();
            count++;
            
            if (count % 100 == 0) {
                insertPs.executeBatch();
            }
        }
        
        if (count % 100 != 0) {
            insertPs.executeBatch();
        }
        
        rs.close();
        ps.close();
        insertPs.close();
        
        return count > 0;
    }
}