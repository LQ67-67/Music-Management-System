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
    // find all active tracks from database
    public List<Track> findAllActive() throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE is_active = 1";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        List<Track> list = new ArrayList<>(); // store the tracks
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

    // find active tracks by keyword
    public List<Track> searchActiveByKeyword(String keyword) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active " + "FROM tracks WHERE is_active = 1 AND (title LIKE ? OR artist LIKE ?)"; // search by title or artist
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

    // find track by ID
    public Track findById(int id) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE id = ?"; // we can find both active and inactive tracks by ID, because we may want to edit or delete an inactive track, so we don't filter by is_active here
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

    // update track stock quantity
    public void updateStock(int trackId, int newStock) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = ? WHERE id = ?"; // we can update stock for both active and inactive tracks, because we may want to restock an inactive track before making it active again
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
        String sql = "INSERT INTO tracks (title, artist, album, genre, price, stock_qty, is_active) VALUES (?, ?, ?, ?, ?, ?, 1)"; // if we want to create an inactive track, we can create it first and update it to set is_active to 0
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
        String sql = "UPDATE tracks SET title = ?, artist = ?, album = ?, genre = ?, price = ?, stock_qty = ? WHERE id = ?"; // update an active track without changing its active status, or update an inactive track before making it active again
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

    // delete track
    public void delete(int trackId) throws SQLException {
        String sql = "UPDATE tracks SET is_active = 0 WHERE id = ?"; // set is_active to 0
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, trackId);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    /* this method is used for real physical deletion (directly deleting that line from the database)
    because this previous codes are used for soft deletion which means we still keep the data in databases even though we delete from the databases
    the database keeps the record, but the UI doesn't show it
    but if u want to really delete the record from the database, u can use this method,
    but be careful when using it, because once deleted, it cannot be recovered

    public void delete(int trackId) throws SQLException {
        String sql = "DELETE FROM tracks WHERE id = ?"; // directly delete the track from the database
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, trackId);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }
     */
}