package controller.User;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import config.StripeKeyConfig;
import database.connectionProvider;
import model.Mail;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MoneyDonationServlet  —  @WebServlet("/MoneyDonation")
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * FLOW:
 *  1. Android Stripe SDK calls createPaymentMethod(CardParams)
 *     → card details go directly to Stripe → returns pm_XXXX
 *  2. Android sends { amount, paymentMethodId: "pm_XXXX" } here
 *     (NO userId in body — read from JWT token set by JwtFilter)
 *  3. Servlet creates + confirms a PaymentIntent with pm_XXXX
 *  4. Saves result to monetary_donations
 *  5. Sends thank-you email to user
 *
 * GET  /MoneyDonation          → history + total (userId from JWT)
 * POST /MoneyDonation          → charge + save + email
 * DELETE /MoneyDonation?donationId=X  → swipe-to-delete
 */
@WebServlet("/MoneyDonation")
public class MoneyDonationServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(MoneyDonationServlet.class.getName());

    // ── GET — history + total ──────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        setHeaders(response);
        PrintWriter out = response.getWriter();

        // ✅ userId from JWT attribute — never from ?userId= query param
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(401);
            out.print("{\"error\":\"Unauthorized\"}");
            out.flush(); return;
        }

        try {
            Connection conn = connectionProvider.getCon();

            // Total successful donations
            PreparedStatement ts = conn.prepareStatement(
                    "SELECT COALESCE(SUM(amount), 0) AS total " +
                    "FROM monetary_donations " +
                    "WHERE user_id = ? AND payment_status = 'SUCCESS'");
            ts.setInt(1, userId);
            ResultSet tr = ts.executeQuery();
            double total = tr.next() ? tr.getDouble("total") : 0;
            tr.close(); ts.close();

            // History (newest first, limit 50)
            PreparedStatement hs = conn.prepareStatement(
                    "SELECT monetary_donation_id, amount, payment_status, donated_at " +
                    "FROM monetary_donations " +
                    "WHERE user_id = ? " +
                    "ORDER BY donated_at DESC LIMIT 50");
            hs.setInt(1, userId);
            ResultSet hr = hs.executeQuery();

            StringBuilder hist = new StringBuilder("[");
            boolean first = true;
            while (hr.next()) {
                if (!first) hist.append(",");
                hist.append("{")
                    .append("\"monetary_donation_id\":").append(hr.getInt("monetary_donation_id")).append(",")
                    .append("\"amount\":").append(hr.getDouble("amount")).append(",")
                    .append("\"payment_status\":\"").append(esc(hr.getString("payment_status"))).append("\",")
                    .append("\"donated_at\":\"").append(esc(hr.getString("donated_at"))).append("\"")
                    .append("}");
                first = false;
            }
            hist.append("]");
            hr.close(); hs.close();

            response.setStatus(200);
            out.print("{\"total_amount\":" + total + ",\"history\":" + hist + "}");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "GET error", e);
            response.setStatus(500);
            out.print("{\"error\":\"Server error\"}");
        }
        out.flush();
    }

    // ── POST — charge pm_XXXX + save + email ──────────────────────────────
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        setHeaders(response);
        PrintWriter out = response.getWriter();

        // ✅ userId from JWT attribute
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(401);
            out.print("{\"status\":\"error\",\"message\":\"Unauthorized\"}");
            out.flush(); return;
        }

        // ── Parse body — only amount + paymentMethodId needed ─────────────
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        double amount;
        String paymentMethodId;
        try {
            JsonObject body = JsonParser.parseString(sb.toString()).getAsJsonObject();
            amount          = body.get("amount").getAsDouble();
            paymentMethodId = body.get("paymentMethodId").getAsString().trim();
        } catch (Exception e) {
            response.setStatus(400);
            out.print("{\"status\":\"error\",\"message\":\"Required: amount, paymentMethodId\"}");
            out.flush(); return;
        }

        // ── Validate ───────────────────────────────────────────────────────
        if (amount <= 0 || amount > 99999) {
            respond400(out, "Invalid amount."); return;
        }
        if (paymentMethodId.isEmpty() || !paymentMethodId.startsWith("pm_")) {
            respond400(out, "Invalid paymentMethodId — must start with pm_"); return;
        }

        // ── Fetch user name + email for thank-you email ────────────────────
        String userEmail = null, userName = "Donor";
        try {
            Connection conn = connectionProvider.getCon();
            PreparedStatement us = conn.prepareStatement(
                    "SELECT name, email FROM users WHERE user_id = ?");
            us.setInt(1, userId);
            ResultSet ur = us.executeQuery();
            if (ur.next()) {
                userName  = ur.getString("name");
                userEmail = ur.getString("email");
            }
            ur.close(); us.close();
        } catch (Exception e) {
            LOGGER.warning("Could not fetch user userId=" + userId + ": " + e.getMessage());
        }

        // ── Stripe — create + confirm PaymentIntent ────────────────────────
        // pm_XXXX was created by the Stripe Android SDK on the device.
        // Raw card numbers never reached this server.
        Stripe.apiKey = StripeKeyConfig.SECRET_KEY;

        String stripeStatus = "FAILED", stripeError = null;

        try {
            long amountCents = Math.round(amount * 100);

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(StripeKeyConfig.CURRENCY)
                    .setPaymentMethod(paymentMethodId)   // ✅ pm_XXXX from Android SDK
                    .addPaymentMethodType("card")         // explicit card — no redirects
                    .setConfirm(true)
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                    .putMetadata("user_id", String.valueOf(userId))
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            LOGGER.info("PaymentIntent " + intent.getId()
                    + " status=" + intent.getStatus() + " user=" + userId);

            if ("succeeded".equals(intent.getStatus())) {
                stripeStatus = "SUCCESS";
            } else if ("requires_action".equals(intent.getStatus())) {
                stripeError = "3D Secure authentication required. Please use a different card.";
            } else {
                stripeError = "Payment not completed — status: " + intent.getStatus();
            }

        } catch (CardException ce) {
            stripeError = ce.getUserMessage();
            if (stripeError == null || stripeError.isEmpty())
                stripeError = "Your card was declined.";
            LOGGER.warning("CardException user=" + userId + ": " + ce.getMessage());
        } catch (StripeException se) {
            stripeError = "Payment processing error. Please try again.";
            LOGGER.log(Level.SEVERE, "StripeException user=" + userId, se);
        }

        // ── Save to monetary_donations (SUCCESS or FAILED) ─────────────────
        int donationId = -1;
        try {
            Connection conn = connectionProvider.getCon();
            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO monetary_donations (user_id, amount, payment_status) " +
                    "VALUES (?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, userId);
            stmt.setDouble(2, amount);
            stmt.setString(3, stripeStatus);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) donationId = keys.getInt(1);
            keys.close(); stmt.close();
            LOGGER.info("Saved donation id=" + donationId
                    + " user=" + userId + " amount=$" + amount + " status=" + stripeStatus);
        } catch (Exception dbEx) {
            LOGGER.log(Level.SEVERE, "DB insert error user=" + userId, dbEx);
            if ("SUCCESS".equals(stripeStatus))
                LOGGER.severe("CRITICAL: Stripe succeeded but DB failed! user=" + userId);
        }

        // ── Send thank-you email async (never blocks the response) ─────────
        if ("SUCCESS".equals(stripeStatus) && userEmail != null && !userEmail.isEmpty()) {
            final String fe = userEmail, fn = userName;
            final double fa = amount;
            final int    fd = donationId;
            new Thread(() -> {
                try {
                    Mail.sendMail(fe,
                            "🩸 Thank You for Your Donation, " + fn + "!",
                            buildThankYouEmail(fn, fa, fd));
                    LOGGER.info("Thank-you email sent to " + fe + " donation #" + fd);
                } catch (Exception e) {
                    LOGGER.warning("Thank-you email failed for " + fe + ": " + e.getMessage());
                }
            }).start();
        }

        // ── Respond ────────────────────────────────────────────────────────
        response.setStatus(200);
        if ("SUCCESS".equals(stripeStatus)) {
            out.print("{\"status\":\"success\",\"donationId\":" + donationId + "}");
        } else {
            out.print("{\"status\":\"error\","
                    + "\"message\":\"" + esc(stripeError != null
                            ? stripeError : "Payment failed. Please try again.") + "\","
                    + "\"donationId\":" + donationId + "}");
        }
        out.flush();
    }

    // ── DELETE — swipe-to-delete from DonateFragment ──────────────────────
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        setHeaders(response);
        PrintWriter out = response.getWriter();

        // ✅ userId from JWT attribute
        Integer userId = (Integer) request.getAttribute("authenticatedUserId");
        if (userId == null) {
            response.setStatus(401);
            out.print("{\"status\":\"error\",\"message\":\"Unauthorized\"}");
            out.flush(); return;
        }

        int donationId = -1;
        try { donationId = Integer.parseInt(request.getParameter("donationId").trim()); }
        catch (Exception ignored) {}

        if (donationId == -1) {
            response.setStatus(400);
            out.print("{\"status\":\"error\",\"message\":\"donationId required\"}");
            out.flush(); return;
        }

        try {
            Connection conn = connectionProvider.getCon();
            // ✅ Only delete if the row belongs to this user — prevents tampering
            PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM monetary_donations " +
                    "WHERE monetary_donation_id = ? AND user_id = ?");
            stmt.setInt(1, donationId);
            stmt.setInt(2, userId);
            int affected = stmt.executeUpdate();
            stmt.close();

            response.setStatus(affected > 0 ? 200 : 404);
            out.print(affected > 0
                    ? "{\"status\":\"success\"}"
                    : "{\"status\":\"error\",\"message\":\"Donation not found\"}");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "DELETE error donationId=" + donationId, e);
            response.setStatus(500);
            out.print("{\"status\":\"error\",\"message\":\"Server error\"}");
        }
        out.flush();
    }

    // ── CORS pre-flight ────────────────────────────────────────────────────
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse res) {
        setHeaders(res);
        res.setStatus(200);
    }

    // ── Thank-you email HTML ───────────────────────────────────────────────
    private String buildThankYouEmail(String name, double amount, int donationId) {
        String a = (amount == (long) amount)
                ? "$" + (long) amount : String.format("$%.2f", amount);

        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#F5F5F5;margin:0;padding:0;'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' style='background:#F5F5F5;padding:30px 0;'>" +
               "<tr><td align='center'>" +
               "<table width='600' cellpadding='0' cellspacing='0' " +
               "style='max-width:600px;width:100%;background:#fff;border-radius:16px;overflow:hidden;" +
               "box-shadow:0 4px 24px rgba(0,0,0,0.08);'>" +

               // Header
               "<tr><td style='background:#DC143C;padding:40px 32px;text-align:center;'>" +
               "<div style='font-size:48px;margin-bottom:12px;'>🩸</div>" +
               "<h1 style='color:#fff;margin:0 0 8px;font-size:24px;'>Thank You, " + escHtml(name) + "!</h1>" +
               "<p style='color:#FFCCCC;margin:0;font-size:14px;'>Your generosity helps save lives every day.</p>" +
               "</td></tr>" +

               // Amount badge
               "<tr><td style='background:#fff;padding:28px 32px 0;text-align:center;'>" +
               "<div style='display:inline-block;background:#FFF0F0;border:2px solid #FFCCCC;" +
               "border-radius:100px;padding:12px 32px;'>" +
               "<span style='font-size:32px;font-weight:700;color:#DC143C;'>" + escHtml(a) + "</span>" +
               "<div style='font-size:10px;color:#AAAAAA;letter-spacing:1px;margin-top:4px;'>DONATION RECEIVED</div>" +
               "</div></td></tr>" +

               // Body
               "<tr><td style='background:#fff;padding:24px 32px 32px;'>" +
               "<p style='color:#333;font-size:15px;line-height:1.7;margin:0 0 16px;'>" +
               "Your donation of <strong style='color:#DC143C;'>" + escHtml(a) + "</strong> " +
               "has been successfully processed and will directly support blood drives, " +
               "donor supplies, and life-saving care." +
               "</p>" +

               // Impact box
               "<table width='100%' cellpadding='0' cellspacing='0' " +
               "style='background:#FFF8F8;border-left:4px solid #DC143C;border-radius:8px;margin:20px 0;'>" +
               "<tr><td style='padding:16px 20px;'>" +
               "<p style='margin:0 0 6px;font-size:12px;font-weight:700;color:#DC143C;" +
               "letter-spacing:0.5px;'>YOUR IMPACT</p>" +
               "<p style='margin:0;font-size:14px;color:#555;line-height:1.6;'>" +
               getImpactText(amount) + "</p>" +
               "</td></tr></table>" +

               // Receipt
               "<table width='100%' cellpadding='0' cellspacing='0' " +
               "style='background:#F9F9F9;border-radius:8px;margin:20px 0;'>" +
               "<tr><td style='padding:14px 20px;font-size:13px;color:#888;'>Reference</td>" +
               "<td style='padding:14px 20px;font-size:13px;color:#333;font-weight:700;" +
               "text-align:right;'>#" + donationId + "</td></tr>" +
               "<tr style='border-top:1px solid #EEE;'>" +
               "<td style='padding:14px 20px;font-size:13px;color:#888;'>Amount</td>" +
               "<td style='padding:14px 20px;font-size:13px;color:#DC143C;font-weight:700;" +
               "text-align:right;'>" + escHtml(a) + "</td></tr>" +
               "<tr style='border-top:1px solid #EEE;'>" +
               "<td style='padding:14px 20px;font-size:13px;color:#888;'>Status</td>" +
               "<td style='padding:14px 20px;font-size:13px;color:#2E7D32;font-weight:700;" +
               "text-align:right;'>✓ Confirmed</td></tr>" +
               "</table>" +

               "<p style='color:#888;font-size:13px;line-height:1.6;margin:20px 0 0;'>" +
               "Every drop counts. Thank you for being part of <strong>OneDrop</strong>.</p>" +
               "</td></tr>" +

               // Footer
               "<tr><td style='background:#1A1A1A;padding:24px 32px;text-align:center;'>" +
               "<p style='color:#888;font-size:12px;margin:0 0 6px;'>" +
               "Sent by OneDrop · Blood Donation Platform</p>" +
               "<p style='color:#555;font-size:11px;margin:0;'>" +
               "Automated confirmation — please do not reply.</p>" +
               "</td></tr>" +

               "</table></td></tr></table></body></html>";
    }

    private String getImpactText(double amount) {
        String a = (amount == (long) amount) ? "$" + (long) amount : String.format("$%.2f", amount);
        if (amount >= 50)
            return "Your generous gift of <strong>" + a + "</strong> can sponsor a " +
                   "<strong>full blood drive event</strong>, enabling dozens of donors to give the gift of life.";
        else if (amount >= 10)
            return "Your donation of <strong>" + a + "</strong> covers " +
                   "<strong>full donor care</strong> for one session — refreshments, testing, and staff support.";
        else if (amount >= 5)
            return "Your donation of <strong>" + a + "</strong> funds a " +
                   "<strong>blood drive supply pack</strong> for one donor.";
        else
            return "Your donation of <strong>" + a + "</strong> provides a " +
                   "<strong>test kit for one donor</strong> — a small gift with a big impact.";
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void respond400(PrintWriter out, String message) {
        out.print("{\"status\":\"error\",\"message\":\"" + esc(message) + "\"}");
        out.flush();
    }

    private void setHeaders(HttpServletResponse r) {
        r.setContentType("application/json");
        r.setCharacterEncoding("UTF-8");
        r.setHeader("Access-Control-Allow-Origin",  "*");
        r.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        r.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}