package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Order;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    public int insert(Order order) throws SQLException {
        try (Connection conn = DBConnectionManager.getConnection()) {
            return insert(conn, order);
        }
    }

    public int insert(Connection conn, Order order) throws SQLException {
        String sql = "INSERT INTO orders (customer_id, user_id, order_date, status, total_amount, shipping_city) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
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
                return -1;
            }
        }
    }

    public void update(Order order) throws SQLException {
        String sql = "UPDATE orders SET status = ?, total_amount = ?, shipping_city = ? WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, order.getStatus());
            ps.setBigDecimal(2, order.getTotalAmount());
            ps.setString(3, order.getShippingCity());
            ps.setInt(4, order.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int orderId) throws SQLException {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    public List<Order> findByUser(int userId) throws SQLException {
        String sql = "SELECT id, customer_id, user_id, order_date, status, total_amount, shipping_city " +
                "FROM orders WHERE user_id = ? ORDER BY order_date DESC";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Order> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        }
    }

    public Order findById(int orderId) throws SQLException {
        String sql = "SELECT id, customer_id, user_id, order_date, status, total_amount, shipping_city " +
                "FROM orders WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        }
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getInt("id"));
        o.setCustomerId(rs.getInt("customer_id"));
        o.setUserId(rs.getInt("user_id"));
        Timestamp ts = rs.getTimestamp("order_date");
        if (ts != null) {
            o.setOrderDate(LocalDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()));
        }
        o.setStatus(rs.getString("status"));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setShippingCity(rs.getString("shipping_city"));
        return o;
    }
}

