package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderItemDao {

    public void insertBatch(List<OrderItem> items) throws SQLException {
        try (Connection conn = DBConnectionManager.getConnection()) {
            insertBatch(conn, items);
        }
    }

    public void insertBatch(Connection conn, List<OrderItem> items) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, track_id, quantity, unit_price, line_total) " +
                "VALUES (?, ?, ?, ?, ?)";
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

    public List<OrderItem> findByOrder(int orderId) throws SQLException {
        String sql = "SELECT id, order_id, track_id, quantity, unit_price, line_total " +
                "FROM order_items WHERE order_id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrderItem> list = new ArrayList<>();
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rs.getInt("id"));
                    item.setOrderId(rs.getInt("order_id"));
                    item.setTrackId(rs.getInt("track_id"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setLineTotal(rs.getBigDecimal("line_total"));
                    list.add(item);
                }
                return list;
            }
        }
    }

    public void deleteByOrder(int orderId) throws SQLException {
        String sql = "DELETE FROM order_items WHERE order_id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }
}

