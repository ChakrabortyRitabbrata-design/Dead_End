-- =========================================================================
-- DEAD END RPG — DATABASE SCHEMA (MySQL 8.0+)
-- =========================================================================

CREATE DATABASE IF NOT EXISTS dead_end;
USE dead_end;

-- 1. Users table for player authentication and profile persistence
CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Scores table for global leaderboard and session records
CREATE TABLE IF NOT EXISTS scores (
    score_id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    score INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Leaderboard query performance indexes
CREATE INDEX IF NOT EXISTS idx_scores_score_date ON scores(score DESC, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_scores_user ON scores(user_id);
