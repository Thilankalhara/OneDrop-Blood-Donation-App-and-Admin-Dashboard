package controller;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Cookie;

@WebServlet("/SessionTestServlet")
public class SessionTestServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        
        // Also print to console
        System.out.println("========================================");
        System.out.println("SessionTestServlet called");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Request URL: " + request.getRequestURL());
        
        HttpSession session = request.getSession(false);
        
        out.println("<style>");
        out.println(".status { padding: 15px; margin: 15px 0; border-radius: 5px; font-weight: bold; }");
        out.println(".success { background: #E8F5E9; color: #2E7D32; border-left: 5px solid #4CAF50; }");
        out.println(".error { background: #FFEBEE; color: #C62828; border-left: 5px solid #F44336; }");
        out.println(".info { background: #E3F2FD; color: #1565C0; border-left: 5px solid #2196F3; }");
        out.println("table { border-collapse: collapse; width: 100%; margin: 15px 0; }");
        out.println("th, td { border: 1px solid #ddd; padding: 12px; text-align: left; }");
        out.println("th { background-color: #DC143C; color: white; }");
        out.println("tr:nth-child(even) { background-color: #f9f9f9; }");
        out.println("</style>");
        
        if (session == null) {
            System.out.println("❌ NO SESSION EXISTS!");
            out.println("<div class='status error'>");
            out.println("<strong>❌ NO SESSION EXISTS!</strong><br>");
            out.println("This means you're not logged in or session expired.<br>");
            out.println("<a href='admin_login.html'>Go to Login Page</a>");
            out.println("</div>");
        } else {
            System.out.println("✓ Session exists: " + session.getId());
            
            out.println("<div class='status success'>");
            out.println("<strong>✅ Session Exists!</strong>");
            out.println("</div>");
            
            out.println("<div class='status info'>");
            out.println("<strong>Session Information:</strong><br>");
            out.println("<strong>Session ID:</strong> " + session.getId() + "<br>");
            out.println("<strong>Creation Time:</strong> " + new java.util.Date(session.getCreationTime()) + "<br>");
            out.println("<strong>Last Accessed:</strong> " + new java.util.Date(session.getLastAccessedTime()) + "<br>");
            out.println("<strong>Max Inactive Interval:</strong> " + session.getMaxInactiveInterval() + " seconds (" + (session.getMaxInactiveInterval() / 60) + " minutes)<br>");
            out.println("<strong>Is New:</strong> " + session.isNew());
            out.println("</div>");
            
            out.println("<h3>Session Attributes:</h3>");
            out.println("<table>");
            out.println("<tr><th>Attribute Name</th><th>Value</th><th>Type</th></tr>");
            
            Enumeration<String> attributeNames = session.getAttributeNames();
            boolean hasAttributes = false;
            
            while (attributeNames.hasMoreElements()) {
                hasAttributes = true;
                String name = attributeNames.nextElement();
                Object value = session.getAttribute(name);
                String type = value != null ? value.getClass().getName() : "null";
                
                System.out.println("  Attribute: " + name + " = " + value + " (" + type + ")");
                
                String rowStyle = "";
                if ("adminId".equals(name)) {
                    rowStyle = "style='background-color: #90EE90; font-weight: bold;'";
                }
                
                out.println("<tr " + rowStyle + ">");
                out.println("<td><strong>" + name + "</strong></td>");
                out.println("<td>" + value + "</td>");
                out.println("<td><small>" + type + "</small></td>");
                out.println("</tr>");
            }
            
            if (!hasAttributes) {
                System.out.println("  No attributes in session!");
                out.println("<tr><td colspan='3' style='color:red; text-align: center;'>");
                out.println("⚠️ No attributes in session! You may need to login again.");
                out.println("</td></tr>");
            }
            
            out.println("</table>");
            
            // Check specifically for adminId
            Object adminId = session.getAttribute("adminId");
            if (adminId != null) {
                System.out.println("✓ adminId found: " + adminId);
                out.println("<div class='status success'>");
                out.println("<strong>✅ adminId FOUND in session!</strong><br>");
                out.println("Value: " + adminId + "<br>");
                out.println("Type: " + adminId.getClass().getName() + "<br>");
                out.println("<strong>AlertsServlet should work!</strong>");
                out.println("</div>");
            } else {
                System.out.println("❌ adminId NOT found in session!");
                out.println("<div class='status error'>");
                out.println("<strong>❌ adminId NOT FOUND in session!</strong><br>");
                out.println("This is why alerts are failing.<br>");
                out.println("You need to logout and login again.<br>");
                out.println("<a href='LogoutServlet'>Logout</a> | <a href='admin_login.html'>Login</a>");
                out.println("</div>");
            }
        }
        
        // Check cookies
        out.println("<h3>Cookies:</h3>");
        out.println("<table>");
        out.println("<tr><th>Cookie Name</th><th>Value</th></tr>");
        
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            for (Cookie cookie : cookies) {
                System.out.println("  Cookie: " + cookie.getName() + " = " + cookie.getValue());
                out.println("<tr><td>" + cookie.getName() + "</td><td>" + cookie.getValue() + "</td></tr>");
            }
        } else {
            System.out.println("  No cookies found!");
            out.println("<tr><td colspan='2' style='text-align: center;'>No cookies found</td></tr>");
        }
        
        out.println("</table>");
        
        System.out.println("========================================");
    }
}