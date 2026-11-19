package db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Game Scores and Leaderboard data.
 */
public final class ScoreDAO {

    private ScoreDAO() {}

    public static void saveScore(int userId, int score) throws SQLException {
        if (userId <= 0 || score < 0) return;

        String sql = "INSERT INTO scores (user_id, score) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, score);
            ps.executeUpdate();
        }
    }

    public static List<ScoreEntry> getTopScores(int limit) throws SQLException {
        List<ScoreEntry> list = new ArrayList<>();
        String sql = "SELECT s.score_id, s.user_id, u.username, s.score, s.created_at " +
                     "FROM scores s " +
                     "JOIN users u ON s.user_id = u.user_id " +
                     "ORDER BY s.score DESC, s.created_at ASC " +
                     "LIMIT ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 1;
                while (rs.next()) {
                    int scoreId = rs.getInt("score_id");
                    int userId = rs.getInt("user_id");
                    String username = rs.getString("username");
                    int score = rs.getInt("score");
                    Timestamp createdAt = rs.getTimestamp("created_at");

                    list.add(new ScoreEntry(rank++, scoreId, userId, username, score, createdAt));
                }
            }
        }
        return list;
    }

    public static int getUserHighScore(int userId) {
        if (userId <= 0) return 0;
        String sql = "SELECT MAX(score) FROM scores WHERE user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            // Silently return 0 on connection failure
        }
        return 0;
    }

    public static int getUserBestRank(int userId) {
        int bestScore = getUserHighScore(userId);
        if (bestScore <= 0) return -1;

        String sql = "SELECT COUNT(DISTINCT user_id) + 1 FROM scores WHERE score > ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bestScore);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            return -1;
        }
        return -1;
    }
}
