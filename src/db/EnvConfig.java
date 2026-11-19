package db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads configuration properties from a local .env file.
 * Safely parses credentials without exposing sensitive information.
 */
public final class EnvConfig {

    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/dead_end";
    private static final String DEFAULT_USER = "dead_end_app";

    private static final Map<String, String> values = new HashMap<>();
    private static boolean loaded = false;

    private EnvConfig() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        File envFile = new File(".env");
        if (!envFile.exists()) {
            // Check parent directory if run from subfolder
            envFile = new File("../.env");
        }

        if (envFile.exists() && envFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String val = line.substring(eqIdx + 1).trim();
                        // Remove surrounding quotes if present
                        if ((val.startsWith("\"") && val.endsWith("\"")) ||
                            (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        values.put(key, val);
                    }
                }
            } catch (IOException e) {
                // Silently fallback to defaults/system env
            }
        }
    }

    public static String get(String key, String defaultValue) {
        load();
        String val = values.get(key);
        if (val == null || val.isEmpty()) {
            val = System.getenv(key);
        }
        return (val != null && !val.isEmpty()) ? val : defaultValue;
    }

    public static String getDbUrl() {
        return get("DB_URL", DEFAULT_URL);
    }

    public static String getDbUsername() {
        return get("DB_USERNAME", DEFAULT_USER);
    }

    public static String getDbPassword() {
        return get("DB_PASSWORD", "");
    }
}
