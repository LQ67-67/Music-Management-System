package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Order;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    private static final String ORDER_COLUMNS = "id, customer_id, user_id, order_date, status, total_amount, shipping_city";

    // insert new order
    public int insert(Order order) throws SQLException {
        String sql = "INSERT INTO orders (customer_id, user_id, order_date, status, total_amount, shipping_city) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getCustomerId());
            ps.setInt(2, order.getUserId());
            ps.setTimestamp(3, Timestamp.valueOf(order.getOrderDate()));
            ps.setString(4, order.getStatus());
            ps.setBigDecimal(5, order.getTotalAmount());
            ps.setString(6, order.getShippingCity());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // update existing order (status, amount, shipping city)
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

    // update only status and shipping city (admin quick edit)
    public void updateStatusAndCity(int orderId, String status, String shippingCity) throws SQLException {
        String sql = "UPDATE orders SET status = ?, shipping_city = ? WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, shippingCity);
            ps.setInt(3, orderId);
            ps.executeUpdate();
        }
    }

    // delete order; order items are removed by database cascade
    public void delete(int orderId) throws SQLException {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    // find orders by user ID, most recent first
    public List<Order> findByUser(int userId) throws SQLException {
        String sql = "SELECT " + ORDER_COLUMNS + " FROM orders WHERE user_id = ? ORDER BY order_date DESC";
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

    // find order by ID
    public Order findById(int orderId) throws SQLException {
        String sql = "SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = ?";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * Flat summary rows for the admin orders table: order id, username, customer name,
     * order date, status, total amount, shipping city. Lives here so the controller
     * never writes SQL itself.
     */
    public List<String[]> findOrderSummaries() throws SQLException {
        String sql = "SELECT o.id, u.username, c.name, o.order_date, o.status, o.total_amount, o.shipping_city "
                + "FROM orders o JOIN users u ON o.user_id = u.id JOIN customers c ON o.customer_id = c.id "
                + "ORDER BY o.order_date DESC";
        try (Connection conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<String[]> rows = new ArrayList<>();
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("order_date");
                rows.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("username"),
                        rs.getString("name"),
                        ts != null ? ts.toLocalDateTime().toString() : "",
                        rs.getString("status"),
                        rs.getString("total_amount"),
                        rs.getString("shipping_city")
                });
            }
            return rows;
        }
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getInt("id"));
        o.setCustomerId(rs.getInt("customer_id"));
        o.setUserId(rs.getInt("user_id"));

        Timestamp ts = rs.getTimestamp("order_date");
        if (ts != null) {
            o.setOrderDate(LocalDateTime.ofInstant(ts.toInstant(), java.time.ZoneId.systemDefault()));
        }

        o.setStatus(rs.getString("status"));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setShippingCity(rs.getString("shipping_city"));
        return o;
    }
}
