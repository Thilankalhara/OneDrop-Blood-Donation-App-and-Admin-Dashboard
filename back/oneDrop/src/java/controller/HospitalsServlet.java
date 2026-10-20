package controller;

import database.connectionProvider;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.ServletException;

@WebServlet("/HospitalsServlet")
public class HospitalsServlet extends HttpServlet {

    private static class Hospital {
        int id;
        String name;
        String phone;
        double latitude;
        double longitude;
        String status;

        Hospital(int id, String name, String phone, double latitude, double longitude, String status) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.latitude = latitude;
            this.longitude = longitude;
            this.status = status;
        }
    }

    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        List<Hospital> hospitals = new ArrayList<>();
        try {
            ResultSet rs = connectionProvider.executeSearch("SELECT * FROM hospitals ORDER BY hospital_id DESC");
            while(rs.next()){
                hospitals.add(new Hospital(
                    rs.getInt("hospital_id"),
                    rs.getString("name"),
                    rs.getString("phone"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("status")
                ));
            }
        } catch(Exception e){ e.printStackTrace(); }
        PrintWriter out = response.getWriter();
        out.print(gson.toJson(hospitals));
        out.flush();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String name = request.getParameter("name");
            String phone = request.getParameter("phone");
            double lat = Double.parseDouble(request.getParameter("lat"));
            double lng = Double.parseDouble(request.getParameter("lng"));
            String status = request.getParameter("status");

            String sql = "INSERT INTO hospitals(name,phone,latitude,longitude,status) VALUES(?,?,?,?,?)";
            PreparedStatement ps = connectionProvider.getCon().prepareStatement(sql);
            ps.setString(1,name);
            ps.setString(2,phone);
            ps.setDouble(3,lat);
            ps.setDouble(4,lng);
            ps.setString(5,status);
            ps.executeUpdate();
            response.getWriter().print("success");
        } catch(Exception e){ e.printStackTrace(); response.getWriter().print("fail"); }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try{
            int id = Integer.parseInt(request.getParameter("id"));
            String sql = "DELETE FROM hospitals WHERE hospital_id=?";
            PreparedStatement ps = connectionProvider.getCon().prepareStatement(sql);
            ps.setInt(1,id);
            ps.executeUpdate();
            response.getWriter().print("success");
        } catch(Exception e){ e.printStackTrace(); response.getWriter().print("fail"); }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try{
            int id = Integer.parseInt(request.getParameter("id"));
            String status = request.getParameter("status");
            String sql = "UPDATE hospitals SET status=? WHERE hospital_id=?";
            PreparedStatement ps = connectionProvider.getCon().prepareStatement(sql);
            ps.setString(1,status);
            ps.setInt(2,id);
            ps.executeUpdate();
            response.getWriter().print("success");
        } catch(Exception e){ e.printStackTrace(); response.getWriter().print("fail"); }
    }
}
