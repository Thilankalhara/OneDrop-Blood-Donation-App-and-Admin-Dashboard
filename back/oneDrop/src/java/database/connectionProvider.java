package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class connectionProvider {

    private static Connection connection;

    // Create connection if not exists
    public static void createConnection() throws Exception {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(
                    System.getenv("DB_URL") != null ? System.getenv("DB_URL") : "jdbc:mysql://localhost:3306/one_drop?useSSL=false&allowPublicKeyRetrieval=true",
                    System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "YOUR_DB_USER",
                    System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "YOUR_DB_PASSWORD"
                );
                System.out.println("Database connected successfully!");
            } catch (SQLException e) {
                System.err.println("Database connection failed: " + e.getMessage());
                throw e;
            }
        }
    }

    // Get the connection
    public static Connection getCon() throws Exception {
        createConnection();
        return connection;
    }

    // Execute SELECT queries
    public static ResultSet executeSearch(String query) throws Exception {
        createConnection();
        Statement stmt = connection.createStatement();
        return stmt.executeQuery(query);
    }

    // Execute INSERT, UPDATE, DELETE
    public static int executeIUD(String query) throws Exception {
        createConnection();
        Statement stmt = connection.createStatement();
        return stmt.executeUpdate(query);
    }

    // Close connection (optional)
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("Database connection closed.");
            } catch (SQLException e) {
                System.err.println("Error closing database connection: " + e.getMessage());
            }
        }
    }
}
