package controller;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/detectServer")
public class DetectServerServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get the IPv4 address of the server hosting the servlet
            String serverIP = getLocalIPAddress();
            int serverPort = request.getLocalPort(); // Get the port where the servlet is running

            if (serverIP == null) {
                throw new Exception("Could not detect server IP address");
            }
            
            // Create a JSON response with server details
            String jsonResponse = String.format("{\"server_ip\": \"%s\", \"server_port\": \"%d\"}", serverIP, serverPort);

            // Write response
            response.getWriter().write(jsonResponse);
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Unable to determine server details.\"}");
        }
    }

    /**
     * Gets the first non-loopback IPv4 address of the running server.
     *
     * @return the IPv4 address as a String, or null if none found
     * @throws SocketException if there's an error with the network interfaces
     */
    private String getLocalIPAddress() throws SocketException {
        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }

            Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress inetAddress = inetAddresses.nextElement();
                // Skip IPv6 and loopback addresses
                if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') == -1) {
                    return inetAddress.getHostAddress(); // Return the first IPv4 address
                }
            }
        }
        return null;
    }
}