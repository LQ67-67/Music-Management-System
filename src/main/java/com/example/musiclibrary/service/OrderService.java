package com.example.musiclibrary.service;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderService {

    // Create a new order with cart items
    public Order createOrder(int customerId, int userId, List<OrderItem> cartItems, String shippingCity) throws SQLException {
        if (customerId <= 0 || userId <= 0) {
            throw new IllegalArgumentException("Invalid customer or user id");
        }

        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        try (Connection conn = DBConnectionManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                BigDecimal total = BigDecimal.ZERO;
                List<OrderItem> itemsToInsert = new ArrayList<>();

                for (OrderItem item : cartItems) {
                    if (item == null) {
                        throw new IllegalArgumentException("Cart contains an invalid item");
                    }

                    Track track = findTrackByIdForUpdate(conn, item.getTrackId());
                    if (track == null || !track.isActive()) {
                        throw new IllegalArgumentException("Track not available: " + item.getTrackId());
                    }

                    if (item.getQuantity() <= 0 || item.getQuantity() > track.getStockQty()) {
                        throw new IllegalArgumentException("Invalid quantity for track " + track.getTitle());
                    }

                    BigDecimal price = track.getPrice();
                    if (price == null) {
                        throw new IllegalArgumentException("Track price is missing for track " + track.getTitle());
                    }

                    BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(item.getQuantity()));
                    item.setUnitPrice(price);
                    item.setLineTotal(lineTotal);
                    total = total.add(lineTotal);
                    itemsToInsert.add(item);

                    updateTrackStock(conn, track.getId(), track.getStockQty() - item.getQuantity());
                }

                Order order = new Order();
                order.setCustomerId(customerId);
                order.setUserId(userId);
                order.setOrderDate(LocalDateTime.now());
                order.setStatus("PENDING");
                order.setTotalAmount(total);
                order.setShippingCity(shippingCity);

                int orderId = insertOrder(conn, order);
                order.setId(orderId);

                for (OrderItem item : itemsToInsert) {
                    item.setOrderId(orderId);
                }
                insertOrderItems(conn, itemsToInsert);

                conn.commit();
                return order;
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private Track findTrackByIdForUpdate(Connection conn, int trackId) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trackId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

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
    }

    private void updateTrackStock(Connection conn, int trackId, int newStock) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setInt(2, trackId);
            int updatedRows = ps.executeUpdate();
            if (updatedRows != 1) {
                throw new SQLException("Failed to update stock for track: " + trackId);
            }
        }
    }

    private int insertOrder(Connection conn, Order order) throws SQLException {
        String sql = "INSERT INTO orders (customer_id, user_id, order_date, status, total_amount, shipping_city) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getCustomerId());
            ps.setInt(2, order.getUserId());
            ps.setTimestamp(3, Timestamp.valueOf(order.getOrderDate()));
            ps.setString(4, order.getStatus());
            ps.setBigDecimal(5, order.getTotalAmount());
            ps.setString(6, order.getShippingCity());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to create order");
    }

    private void insertOrderItems(Connection conn, List<OrderItem> items) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, track_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OrderItem item : items) {
                ps.setInt(1, item.getOrderId());
                ps.setInt(2, item.getTrackId());
                ps.setInt(3, item.getQuantity());
                ps.setBigDecimal(4, item.getUnitPrice());
                ps.setBigDecimal(5, item.getLineTotal());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}