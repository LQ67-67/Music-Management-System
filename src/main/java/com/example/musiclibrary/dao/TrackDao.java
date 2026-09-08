package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Track;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TrackDao {

    private static final String TRACK_COLUMNS = "id, title, artist, album, genre, price, stock_qty, is_active";

    // find all active tracks from database
    public List<Track> findAllActive() throws SQLException {
        String sql = "SELECT " + TRACK_COLUMNS + " FROM tracks WHERE is_active = 1 ORDER BY title";
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

    // find active tracks by keyword
    public List<Track> searchActiveByKeyword(String keyword) throws SQLException {
        String sql = "SELECT " + TRACK_COLUMNS + " FROM tracks WHERE is_active = 1 AND (title LIKE ? OR artist LIKE ?) ORDER BY title";
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

    // find track by ID (both active and inactive — needed for editing/restocking)
    public Track findById(int id) throws SQLException {
        String sql = "SELECT " + TRACK_COLUMNS + " FROM tracks WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // restock by a signed delta; never lets stock go below zero
    public void adjustStock(int trackId, int delta) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = GREATEST(0, stock_qty + ?) WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, delta);
            ps.setInt(2, trackId);
            ps.executeUpdate();
        }
    }

    // update track stock quantity (absolute)
    public void updateStock(int trackId, int newStock) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = ? WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setInt(2, trackId);
            ps.executeUpdate();
        }
    }

    // Create new track
    public int create(Track track) throws SQLException {
        String sql = "INSERT INTO tracks (title, artist, album, genre, price, stock_qty, is_active) VALUES (?, ?, ?, ?, ?, ?, 1)";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, track.getTitle());
            ps.setString(2, track.getArtist());
            ps.setString(3, track.getAlbum());
            ps.setString(4, track.getGenre());
            ps.setBigDecimal(5, track.getPrice());
            ps.setInt(6, track.getStockQty());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // Update existing track
    public void update(Track track) throws SQLException {
        String sql = "UPDATE tracks SET title = ?, artist = ?, album = ?, genre = ?, price = ?, stock_qty = ? WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, track.getTitle());
            ps.setString(2, track.getArtist());
            ps.setString(3, track.getAlbum());
            ps.setString(4, track.getGenre());
            ps.setBigDecimal(5, track.getPrice());
            ps.setInt(6, track.getStockQty());
            ps.setInt(7, track.getId());
            ps.executeUpdate();
        }
    }

    // delete track (soft delete — the row stays for order history, UI hides it)
    public void delete(int trackId) throws SQLException {
        String sql = "UPDATE tracks SET is_active = 0 WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trackId);
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
