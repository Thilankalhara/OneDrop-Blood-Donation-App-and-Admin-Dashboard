package model;

import util.JwtUtil;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.*;
import java.io.IOException;


@WebFilter(urlPatterns = {
        "/HomeDataServlet",
        "/LeaderboardServlet",
        "/ScanQRServlet",
        "/ProfileServlet",
        "/MoneyDonation",
        "/Hospitals",
        "/DeleteAlert",
        "/SessionCheckServlet",
        "/Donations"
        // add new protected servlets here
})
public class JwtFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // ── CORS preflight — allow through without a token ─────────────────
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Access-Control-Allow-Origin",  "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // ── Check Authorization header ─────────────────────────────────────
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid Authorization header.");
            return;
        }

        // ── Validate token ─────────────────────────────────────────────────
        try {
            String token  = header.substring(7); // strip "Bearer "
            int    userId = JwtUtil.validateToken(token);

            // ✅ Attach verified userId — servlets read this, never ?userId= param
            request.setAttribute("authenticatedUserId", userId);
            chain.doFilter(req, res);

        } catch (Exception e) {
            sendUnauthorized(response, "Token is invalid or has expired. Please log in again.");
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.getWriter().print("{\"error\":\"" + message + "\"}");
    }

    @Override public void init(FilterConfig fc) {}
    @Override public void destroy() {}
}