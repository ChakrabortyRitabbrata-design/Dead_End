package db;

import java.sql.Timestamp;

/**
 * Model representing an authenticated user account.
 */
public class User {
    private final int userId;
    private final String username;
    private final Timestamp createdAt;
    private int highScore;

    public User(int userId, String username, Timestamp createdAt) {
        this(userId, username, createdAt, 0);
    }

    public User(int userId, String username, Timestamp createdAt, int highScore) {
        this.userId = userId;
        this.username = username;
        this.createdAt = createdAt;
        this.highScore = highScore;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public int getHighScore() {
        return highScore;
    }

    public void setHighScore(int highScore) {
        this.highScore = highScore;
    }

    @Override
    public String toString() {
        return "User{" + "userId=" + userId + ", username='" + username + '\'' + ", highScore=" + highScore + '}';
    }
}
