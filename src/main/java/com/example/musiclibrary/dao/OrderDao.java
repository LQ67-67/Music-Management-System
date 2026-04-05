package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Order;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    // Insert new order
    public int insert(Order order) throws SQLException {
        String sql = "INSERT INTO orders (customer_id, user_id, order_date, status, total_amount, shipping_city) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

        ps.setInt(1, order.getCustomerId());
        ps.setInt(2, order.getUserId());
        ps.setTimestamp(3, Timestamp.valueOf(order.getOrderDate()));
        ps.setString(4, order.getStatus());
        ps.setBigDecimal(5, order.getTotalAmount());
        ps.setString(6, order.getShippingCity());

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

    // Update existing order
    public void update(Order order) throws SQLException {
        String sql = "UPDATE orders SET status = ?, total_amount = ?, shipping_city = ? WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);

        ps.setString(1, order.getStatus());
        ps.setBigDecimal(2, order.getTotalAmount());
        ps.setString(3, order.getShippingCity());
        ps.setInt(4, order.getId());

        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    // Delete order
    public void delete(int orderId) throws SQLException {
        String sql = "DELETE FROM orders WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, orderId);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    // Find orders by user ID
    public List<Order> findByUser(int userId) throws SQLException {
        String sql = "SELECT id, customer_id, user_id, order_date, status, total_amount, shipping_city " +
                "FROM orders WHERE user_id = ? ORDER BY order_date DESC";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, userId);

        ResultSet rs = ps.executeQuery();

        List<Order> list = new ArrayList<>();
        while (rs.next()) {
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
            list.add(o);
        }

        rs.close();
        ps.close();
        conn.close();
        return list;
    }

    // Find order by ID
    public Order findById(int orderId) throws SQLException {
        String sql = "SELECT id, customer_id, user_id, order_date, status, total_amount, shipping_city " +
                "FROM orders WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, orderId);

        ResultSet rs = ps.executeQuery();
        Order o = null;
        if (rs.next()) {
            o = new Order();
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
        }

        rs.close();
        ps.close();
        conn.close();
        return o;
    }
}