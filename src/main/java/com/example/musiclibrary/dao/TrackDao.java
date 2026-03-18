package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Track;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TrackDao {

    public List<Track> findAllActive() throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE is_active = 1";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Track> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        }
    }

    public List<Track> searchActiveByKeyword(String keyword) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active " +
                "FROM tracks WHERE is_active = 1 AND (title LIKE ? OR artist LIKE ?)";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + keyword + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                List<Track> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    public Track findById(int id) throws SQLException {
        try (Connection conn = DBConnectionManager.getConnection()) {
            return findById(conn, id);
        }
    }

    public void updateStock(int trackId, int newStock) throws SQLException {
        try (Connection conn = DBConnectionManager.getConnection()) {
            updateStock(conn, trackId, newStock);
        }
    }

    public Track findById(Connection conn, int id) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    public void updateStock(Connection conn, int trackId, int newStock) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setInt(2, trackId);
            ps.executeUpdate();
        }
    }

    private Track mapRow(ResultSet rs) throws SQLException {
        Track t = new Track();
        t.setId(rs.getInt("id"));
        t.setTitle(rs.getString("title"));
        t.setArtist(rs.getString("artist"));
        t.setAlbum(rs.getString("album"));
        t.setGenre(rs.getString("genre"));
        t.setPrice(rs.getBigDecimal("price"));
        t.setStockQty(rs.getInt("stock_qty"));
        t.setActive(rs.getBoolean("is_active"));
        return t;
    }
}

