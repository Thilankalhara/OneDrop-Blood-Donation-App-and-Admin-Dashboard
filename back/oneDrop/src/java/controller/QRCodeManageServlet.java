package controller;

import database.connectionProvider;
import com.google.gson.Gson;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet("/QRCodeServlet")
public class QRCodeManageServlet extends HttpServlet {

    private final Gson gson = new Gson();

    // ========================= GET =========================
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");

        try {

            // ========= LIST =========
            if ("list".equals(action)) {

                List<Map<String, Object>> qrList = new ArrayList<>();

                ResultSet rs = connectionProvider.executeSearch(
                        "SELECT qr_id, qr_code, status, generated_date FROM qr_codes ORDER BY qr_id DESC"
                );

                while (rs.next()) {
                    Map<String, Object> qr = new HashMap<>();
                    qr.put("qr_id", rs.getInt("qr_id"));
                    qr.put("qr_code", rs.getString("qr_code"));
                    qr.put("status", rs.getString("status"));
                    qr.put("generated_date", rs.getTimestamp("generated_date").toString());
                    qrList.add(qr);
                }

                response.setContentType("application/json");
                response.getWriter().print(gson.toJson(qrList));
                return;
            }

            // ========= DOWNLOAD =========
            if ("download".equals(action)) {

                int qrId = Integer.parseInt(request.getParameter("id"));

                ResultSet rs = connectionProvider.executeSearch(
                        "SELECT qr_code FROM qr_codes WHERE qr_id=" + qrId
                );

                if (!rs.next()) {
                    response.sendError(404, "QR not found");
                    return;
                }

                String qrText = rs.getString("qr_code");

                QRCodeWriter qrWriter = new QRCodeWriter();
                BitMatrix matrix = qrWriter.encode(qrText, BarcodeFormat.QR_CODE, 300, 300);

                response.setContentType("image/png");
                response.setHeader("Content-Disposition", "attachment; filename=QRCode_" + qrId + ".png");

                OutputStream os = response.getOutputStream();
                MatrixToImageWriter.writeToStream(matrix, "PNG", os);
                os.close();
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(500, "Server Error");
        }
    }

    // ========================= POST =========================
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        try {

            // ========= GENERATE =========
            if ("generate".equals(action)) {

                String qrText = UUID.randomUUID().toString();

                int r = connectionProvider.executeIUD(
                        "INSERT INTO qr_codes (qr_code, status) VALUES ('" + qrText + "', 'UNUSED')"
                );

                Map<String, Object> res = new HashMap<>();
                res.put("success", r > 0);

                out.print(gson.toJson(res));
                return;
            }

            // ========= PUBLISH (SET USED) =========
            if ("publish".equals(action)) {

                String[] ids = request.getParameterValues("ids[]");

                if (ids != null && ids.length > 0) {
                    for (String id : ids) {
                        connectionProvider.executeIUD(
                                "UPDATE qr_codes SET status='USED' WHERE qr_id=" + id
                        );
                    }
                    out.print("{\"success\":true}");
                } else {
                    out.print("{\"success\":false, \"message\":\"No IDs provided\"}");
                }
                return;
            }

            // ========= DEACTIVATE =========
            if ("deactivate".equals(action)) {

                String[] ids = request.getParameterValues("ids[]");

                if (ids != null && ids.length > 0) {
                    for (String id : ids) {
                        connectionProvider.executeIUD(
                                "UPDATE qr_codes SET status='INACTIVE' WHERE qr_id=" + id
                        );
                    }
                    out.print("{\"success\":true}");
                } else {
                    out.print("{\"success\":false, \"message\":\"No IDs provided\"}");
                }
                return;
            }

            // ========= DELETE =========
            if ("delete".equals(action)) {

                String[] ids = request.getParameterValues("ids[]");

                if (ids != null && ids.length > 0) {
                    for (String id : ids) {
                        connectionProvider.executeIUD(
                                "DELETE FROM qr_codes WHERE qr_id=" + id
                        );
                    }
                    out.print("{\"success\":true}");
                } else {
                    out.print("{\"success\":false, \"message\":\"No IDs provided\"}");
                }
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.print("{\"success\":false, \"message\":\"" + e.getMessage() + "\"}");
        }
    }
}