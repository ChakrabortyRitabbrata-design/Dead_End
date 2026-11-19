package db;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/**
 * Model representing a record on the global leaderboard.
 */
public class ScoreEntry {
    private final int rank;
    private final int scoreId;
    private final int userId;
    private final String username;
    private final int score;
    private final Timestamp createdAt;

    public ScoreEntry(int rank, int scoreId, int userId, String username, int score, Timestamp createdAt) {
        this.rank = rank;
        this.scoreId = scoreId;
        this.userId = userId;
        this.username = username;
        this.score = score;
        this.createdAt = createdAt;
    }

    public int getRank() {
        return rank;
    }

    public int getScoreId() {
        return scoreId;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public int getScore() {
        return score;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public String getFormattedDate() {
        if (createdAt == null) return "N/A";
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(createdAt);
    }
}
