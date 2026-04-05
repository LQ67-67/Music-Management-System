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

    // Find all active tracks from database
    public List<Track> findAllActive() throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE is_active = 1";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        List<Track> list = new ArrayList<>();
        while (rs.next()) {
            Track t = new Track();
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setArtist(rs.getString("artist"));
            t.setAlbum(rs.getString("album"));
            t.setGenre(rs.getString("genre"));
            t.setPrice(rs.getBigDecimal("price"));
            t.setStockQty(rs.getInt("stock_qty"));
            t.setActive(rs.getBoolean("is_active"));
            list.add(t);
        }

        rs.close();
        ps.close();
        conn.close();
        return list;
    }

    // Search active tracks by keyword
    public List<Track> searchActiveByKeyword(String keyword) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active " +
                "FROM tracks WHERE is_active = 1 AND (title LIKE ? OR artist LIKE ?)";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);

        String pattern = "%" + keyword + "%";
        ps.setString(1, pattern);
        ps.setString(2, pattern);

        ResultSet rs = ps.executeQuery();

        List<Track> list = new ArrayList<>();
        while (rs.next()) {
            Track t = new Track();
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setArtist(rs.getString("artist"));
            t.setAlbum(rs.getString("album"));
            t.setGenre(rs.getString("genre"));
            t.setPrice(rs.getBigDecimal("price"));
            t.setStockQty(rs.getInt("stock_qty"));
            t.setActive(rs.getBoolean("is_active"));
            list.add(t);
        }

        rs.close();
        ps.close();
        conn.close();
        return list;
    }

    // Find track by ID
    public Track findById(int id) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, id);

        ResultSet rs = ps.executeQuery();
        Track t = null;
        if (rs.next()) {
            t = new Track();
            t.setId(rs.getInt("id"));
            t.setTitle(rs.getString("title"));
            t.setArtist(rs.getString("artist"));
            t.setAlbum(rs.getString("album"));
            t.setGenre(rs.getString("genre"));
            t.setPrice(rs.getBigDecimal("price"));
            t.setStockQty(rs.getInt("stock_qty"));
            t.setActive(rs.getBoolean("is_active"));
        }

        rs.close();
        ps.close();
        conn.close();
        return t;
    }

    // Update track stock quantity
    public void updateStock(int trackId, int newStock) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = ? WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, newStock);
        ps.setInt(2, trackId);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    // Create new track
    public int create(Track track) throws SQLException {
        String sql = "INSERT INTO tracks (title, artist, album, genre, price, stock_qty, is_active) VALUES (?, ?, ?, ?, ?, ?, 1)";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);

        ps.setString(1, track.getTitle());
        ps.setString(2, track.getArtist());
        ps.setString(3, track.getAlbum());
        ps.setString(4, track.getGenre());
        ps.setBigDecimal(5, track.getPrice());
        ps.setInt(6, track.getStockQty());

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

    // Update existing track
    public void update(Track track) throws SQLException {
        String sql = "UPDATE tracks SET title = ?, artist = ?, album = ?, genre = ?, price = ?, stock_qty = ? WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);

        ps.setString(1, track.getTitle());
        ps.setString(2, track.getArtist());
        ps.setString(3, track.getAlbum());
        ps.setString(4, track.getGenre());
        ps.setBigDecimal(5, track.getPrice());
        ps.setInt(6, track.getStockQty());
        ps.setInt(7, track.getId());

        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    // Delete track (set is_active to 0)
    public void delete(int trackId) throws SQLException {
        String sql = "UPDATE tracks SET is_active = 0 WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, trackId);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }
}