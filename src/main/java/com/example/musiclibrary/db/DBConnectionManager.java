package com.example.musiclibrary.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DBConnectionManager {

    private static final String URL = "jdbc:mysql://localhost:3306/music_library?serverTimezone=UTC";
    private static final String USER = "root";      // TODO: 根据你本地设置修改
    private static final String PASSWORD = "";      // TODO: 根据你本地设置修改

    private DBConnectionManager() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}

