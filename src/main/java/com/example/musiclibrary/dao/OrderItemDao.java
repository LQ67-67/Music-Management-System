package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderItemDao {

    // Insert multiple order items at once
    public void insertBatch(List<OrderItem> items) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, track_id, quantity, unit_price, line_total) " +
                "VALUES (?, ?, ?, ?, ?)";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);

        for (OrderItem item : items) {
            ps.setInt(1, item.getOrderId());
            ps.setInt(2, item.getTrackId());
            ps.setInt(3, item.getQuantity());
            ps.setBigDecimal(4, item.getUnitPrice());
            ps.setBigDecimal(5, item.getLineTotal());
            ps.addBatch();
        }

        ps.executeBatch();
        ps.close();
        conn.close();
    }

    // Find order items by order ID
    public List<OrderItem> findByOrder(int orderId) throws SQLException {
        String sql = "SELECT id, order_id, track_id, quantity, unit_price, line_total " +
                "FROM order_items WHERE order_id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, orderId);

        ResultSet rs = ps.executeQuery();

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

        rs.close();
        ps.close();
        conn.close();
        return list;
    }

    // Delete order items by order ID
    public void deleteByOrder(int orderId) throws SQLException {
        String sql = "DELETE FROM order_items WHERE order_id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, orderId);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }
}