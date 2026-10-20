package controller;

import database.connectionProvider;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/AdminLoginServlet")
public class AdminLoginServlet extends HttpServlet {
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        
        System.out.println("=================================");
        System.out.println("Login attempt for: " + email);
        
        try {
            // Use PreparedStatement to prevent SQL injection
            String query = "SELECT admin_id, name, email FROM admins WHERE email = ? AND password = ?";
            Connection con = connectionProvider.getCon();
            PreparedStatement ps = con.prepareStatement(query);
            ps.setString(1, email);
            ps.setString(2, password);
            
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                // Get admin data
                int adminId = rs.getInt("admin_id");
                String name = rs.getString("name");
                String adminEmail = rs.getString("email");
                
                System.out.println("✓ Admin found in database:");
                System.out.println("  - Admin ID: " + adminId);
                System.out.println("  - Name: " + name);
                
                // Invalidate old session if exists to prevent conflicts
                HttpSession oldSession = request.getSession(false);
                if (oldSession != null) {
                    System.out.println("  - Invalidating old session: " + oldSession.getId());
                    oldSession.invalidate();
                }
                
                // Create NEW session with explicit true parameter
                HttpSession session = request.getSession(true);
                session.setMaxInactiveInterval(30 * 60); // 30 minutes
                
                // Store ALL necessary attributes
                session.setAttribute("adminId", Integer.valueOf(adminId)); // Ensure it's Integer object
                session.setAttribute("adminName", name);
                session.setAttribute("adminEmail", adminEmail);
                session.setAttribute("user", name); // For AuthFilter
                
                System.out.println("✓ New session created: " + session.getId());
                System.out.println("  - adminId stored: " + session.getAttribute("adminId"));
                System.out.println("  - user stored: " + session.getAttribute("user"));
                System.out.println("  - Session timeout: " + session.getMaxInactiveInterval() + " seconds");
                System.out.println("=================================");
                
                response.getWriter().write("success");
            } else {
                System.out.println("✗ Login failed - Invalid credentials");
                System.out.println("=================================");
                response.getWriter().write("fail");
            }
            
            rs.close();
            ps.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("✗ Login error: " + e.getMessage());
            System.out.println("=================================");
            response.getWriter().write("fail");
        }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("admin_login.html");
    }
}