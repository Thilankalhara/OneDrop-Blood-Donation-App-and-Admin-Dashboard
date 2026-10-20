package model;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;

@WebFilter("/*")
public class NoCacheFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        
        // Don't add no-cache headers for AJAX/Servlet requests
        String uri = request.getRequestURI();
        if (!uri.endsWith("Servlet") && !uri.contains("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
        
        chain.doFilter(req, res);
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("NoCacheFilter initialized");
    }
    
    @Override
    public void destroy() {
        System.out.println("NoCacheFilter destroyed");
    }
}