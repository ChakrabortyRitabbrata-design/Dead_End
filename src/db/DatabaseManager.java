package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database connectivity and schema initialization for Dead End.
 */
public final class DatabaseManager {

    private static volatile boolean available = false;
    private static volatile String lastError = null;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // Driver will be discovered by DriverManager if on module path/classpath
        }
    }

    private DatabaseManager() {}

    public static synchronized boolean checkConnection() {
        try (Connection conn = getConnection()) {
            available = (conn != null && !conn.isClosed());
            lastError = null;
            if (available) {
                initializeTables(conn);
            }
            return available;
        } catch (Exception e) {
            available = false;
            lastError = e.getMessage();
            return false;
        }
    }

    public static Connection getConnection() throws SQLException {
        String url = EnvConfig.getDbUrl();
        String user = EnvConfig.getDbUsername();
        String pass = EnvConfig.getDbPassword();
        return DriverManager.getConnection(url, user, pass);
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String getLastError() {
        return lastError != null ? lastError : "Database unavailable";
    }

    private static void initializeTables(Connection conn) {
        String createUsersSql = "CREATE TABLE IF NOT EXISTS users (" +
                "user_id INT PRIMARY KEY AUTO_INCREMENT, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String createScoresSql = "CREATE TABLE IF NOT EXISTS scores (" +
                "score_id INT PRIMARY KEY AUTO_INCREMENT, " +
                "user_id INT NOT NULL, " +
                "score INT NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createUsersSql);
            stmt.executeUpdate(createScoresSql);
        } catch (SQLException e) {
            // Tables might already exist or user lacks DDL privileges (tables were already created in Workbench)
        }
    }
}
