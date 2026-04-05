package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserDao {

    // Find user by username
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT id, username, password_hash, role FROM users WHERE username = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, username);

        ResultSet rs = ps.executeQuery();
        User user = null;
        if (rs.next()) {
            user = new User();
            user.setId(rs.getInt("id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setRole(rs.getString("role"));
        }

        rs.close();
        ps.close();
        conn.close();
        return user;
    }

    // Create new user
    public int create(String username, String password, String role) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);

        ps.setString(1, username);
        ps.setString(2, password);
        ps.setString(3, role);

        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();
        int id = -1;
        if (rs.next()) {
            id = rs.getInt(1);
        }

        rs.close();
        ps.close();
        conn.close();
        return id;
    }
}