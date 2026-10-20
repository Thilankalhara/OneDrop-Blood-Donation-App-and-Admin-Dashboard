package model;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;

@WebFilter(urlPatterns = {
    "/admin_panel.html",
    "/user_management.html",
    "/qr_manage.html",
    "/qr_history.html",
    "/alerts.html",
    "/hospitals.html",
    "/top_donaters.html",
    "/monetary_donations.html" 
})
public class AuthFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        HttpSession session = request.getSession(false);
        
        // Debug logging
        System.out.println("AuthFilter - Checking session for: " + request.getRequestURI());
        
        if (session != null && session.getAttribute("user") != null) {
            System.out.println("AuthFilter - Session valid for user: " + session.getAttribute("user"));
            
            // Make sure adminId is in session (for backward compatibility)
            if (session.getAttribute("adminId") == null) {
                System.out.println("WARNING: user exists but adminId missing - this shouldn't happen!");
            }
            
            chain.doFilter(req, res);
        } else {
            System.out.println("AuthFilter - No valid session, redirecting to login");
            response.sendRedirect("admin_login.html");
        }
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("AuthFilter initialized");
    }
    
    @Override
    public void destroy() {
        System.out.println("AuthFilter destroyed");
    }
}