package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * Data Access Object for User Authentication and Account management.
 */
public final class AuthDAO {

    private AuthDAO() {}

    public static User register(String username, String password) throws SQLException, IllegalArgumentException {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }
        username = username.trim();
        if (username.length() < 3 || username.length() > 20) {
            throw new IllegalArgumentException("Username must be 3-20 characters long.");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Username can only contain letters, numbers, and underscores.");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters long.");
        }

        if (usernameExists(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        String passwordHash = PasswordHasher.hash(password);
        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int userId = rs.getInt(1);
                    return new User(userId, username, new Timestamp(System.currentTimeMillis()));
                }
            }
        }

        throw new SQLException("Failed to retrieve generated user ID.");
    }

    public static User login(String username, String password) throws SQLException, IllegalArgumentException {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Username and password are required.");
        }
        username = username.trim();

        String sql = "SELECT user_id, username, password_hash, created_at FROM users WHERE username = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Invalid username or password.");
                }

                int userId = rs.getInt("user_id");
                String dbUser = rs.getString("username");
                String dbHash = rs.getString("password_hash");
                Timestamp createdAt = rs.getTimestamp("created_at");

                if (!PasswordHasher.verify(password, dbHash)) {
                    throw new IllegalArgumentException("Invalid username or password.");
                }

                int highScore = ScoreDAO.getUserHighScore(userId);
                return new User(userId, dbUser, createdAt, highScore);
            }
        }
    }

    public static boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
