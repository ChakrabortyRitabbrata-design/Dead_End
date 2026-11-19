package db;

/**
 * Manages the active player account session during gameplay.
 */
public final class SessionManager {

    private static volatile User currentUser = null;

    private SessionManager() {}

    public static synchronized void setCurrentUser(User user) {
        currentUser = user;
    }

    public static synchronized User getCurrentUser() {
        return currentUser;
    }

    public static synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    public static synchronized String getUsername() {
        return currentUser != null ? currentUser.getUsername() : "Guest";
    }

    public static synchronized int getUserId() {
        return currentUser != null ? currentUser.getUserId() : -1;
    }

    public static synchronized void logout() {
        currentUser = null;
    }
}
