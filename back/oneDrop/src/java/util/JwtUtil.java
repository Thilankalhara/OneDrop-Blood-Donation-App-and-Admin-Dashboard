package util; // 

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import java.util.logging.Logger;

public class JwtUtil {

    private static final Logger LOGGER = Logger.getLogger(JwtUtil.class.getName());

    // ✅ Must be at least 32 characters for HS256
    private static final String SECRET = System.getenv("JWT_SECRET") != null ? System.getenv("JWT_SECRET") 
            : "YOUR_JWT_SECRET_KEY_MIN_32_CHARS_LONG";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET.getBytes());
    private static final long   EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    public static String generateToken(int userId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + EXPIRY_MS);
        String token = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
        LOGGER.info("JwtUtil: token generated for userId=" + userId);
        return token;
    }

    public static int validateToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
        int userId = Integer.parseInt(claims.getSubject());
        LOGGER.info("JwtUtil: token valid for userId=" + userId);
        return userId;
    }
}