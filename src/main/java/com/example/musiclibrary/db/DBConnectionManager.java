package com.example.musiclibrary.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Central JDBC access point backed by an HikariCP connection pool.
 *
 * Configuration is resolved in this order (first hit wins):
 * 1. JVM system properties:  db.url / db.user / db.password
 * 2. Environment variables:  DB_URL / DB_USER / DB_PASSWORD
 * 3. ./db.properties (project root, git-ignored)
 * 4. Built-in localhost defaults for development
 */
public final class DBConnectionManager {

    private static final HikariDataSource DATA_SOURCE = buildDataSource();

    private DBConnectionManager() {
    }

    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    private static HikariDataSource buildDataSource() {
        var settings = resolveSettings();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.url());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setPoolName("music-library-pool");
        // fail fast on a dead backend instead of hanging the UI thread forever
        config.setConnectionTimeout(8_000);
        config.addDataSourceProperty("serverTimezone", "UTC");

        return new HikariDataSource(config);
    }

    private record DbSettings(String url, String user, String password) {
    }

    private static DbSettings resolveSettings() {
        String url = System.getProperty("db.url", System.getenv("DB_URL"));
        String user = System.getProperty("db.user", System.getenv("DB_USER"));
        String password = System.getProperty("db.password", System.getenv("DB_PASSWORD"));

        if (url == null || user == null) {
            var fileSettings = readFileSettings();
            if (url == null) url = fileSettings.url();
            if (user == null) user = fileSettings.user();
            if (password == null) password = fileSettings.password();
        }

        if (url == null || url.isBlank()) {
            url = "jdbc:mysql://localhost:3306/music_library";
        }
        if (user == null || user.isBlank()) {
            user = "root";
        }
        if (password == null) {
            password = "";
        }
        return new DbSettings(url, user, password);
    }

    private static DbSettings readFileSettings() {
        Path candidate = Path.of("db.properties");
        if (!Files.isRegularFile(candidate)) {
            return new DbSettings(null, null, null);
        }
        var props = new Properties();
        try (InputStream in = Files.newInputStream(candidate)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Ignoring unreadable db.properties: " + e.getMessage());
            return new DbSettings(null, null, null);
        }
        return new DbSettings(
                props.getProperty("db.url"),
                props.getProperty("db.user"),
                props.getProperty("db.password"));
    }
}
