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
    // create new order with cart items
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

                    if (item.getQuantity() <= 0) {
                        throw new IllegalArgumentException("Invalid quantity for track " + track.getTitle());
                    }

                    BigDecimal price = track.getPrice(); // get current price from database
                    if (price == null) {
                        throw new IllegalArgumentException("Track price is missing for track " + track.getTitle());
                    }

                    // atomically deduct stock; fails when another transaction already took the units
                    if (!decrementStockIfAvailable(conn, track.getId(), item.getQuantity())) {
                        throw new IllegalArgumentException("Insufficient stock for track " + track.getTitle());
                    }

                    BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(item.getQuantity())); // calculate line total
                    item.setUnitPrice(price);
                    item.setLineTotal(lineTotal);
                    total = total.add(lineTotal);
                    itemsToInsert.add(item);
                }

                Order order = new Order();
                order.setCustomerId(customerId);
                order.setUserId(userId);
                order.setOrderDate(LocalDateTime.now()); // set order date to current time
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

    // cancel a pending order: restore the reserved stock and mark the order CANCELLED
    public void cancelOrder(int orderId) throws SQLException {
        try (Connection conn = DBConnectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Order order = findOrderByIdForUpdate(conn, orderId);
                if (order == null) {
                    throw new IllegalArgumentException("Order not found: " + orderId);
                }
                if ("PAID".equals(order.getStatus())) {
                    throw new IllegalStateException("Cannot cancel a PAID order. Please contact support.");
                }
                if (!"CANCELLED".equals(order.getStatus())) {
                    restoreStockForOrder(conn, orderId); // give reserved units back to the tracks
                    updateOrderStatus(conn, orderId, "CANCELLED");
                }
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // delete an order permanently; PAID orders are kept as financial records, other statuses return stock first
    public void deleteOrder(int orderId) throws SQLException {
        try (Connection conn = DBConnectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Order order = findOrderByIdForUpdate(conn, orderId);
                if (order == null) {
                    throw new IllegalArgumentException("Order not found: " + orderId);
                }
                if ("PAID".equals(order.getStatus())) {
                    throw new IllegalStateException("Cannot delete a PAID order. Cancel it or contact support instead.");
                }
                if (!"CANCELLED".equals(order.getStatus())) {
                    restoreStockForOrder(conn, orderId); // a live order still holds reserved stock
                }
                deleteOrderItems(conn, orderId);
                deleteOrderRow(conn, orderId);
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // change the quantity of one order item on a PENDING order and settle the stock difference
    public void adjustOrderItemQuantity(int orderId, int orderItemId, int newQuantity) throws SQLException {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1. Use Remove to delete the item.");
        }

        try (Connection conn = DBConnectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                requireEditableOrder(conn, orderId);
                OrderItem item = findOrderItemByIdForUpdate(conn, orderItemId);
                if (item == null || item.getOrderId() != orderId) {
                    throw new IllegalArgumentException("Order item does not belong to this order.");
                }

                int delta = newQuantity - item.getQuantity();
                if (delta > 0) {
                    if (!decrementStockIfAvailable(conn, item.getTrackId(), delta)) {
                        throw new IllegalArgumentException("Not enough stock to increase quantity.");
                    }
                } else if (delta < 0) {
                    adjustStock(conn, item.getTrackId(), -delta); // return the unused units
                }

                if (delta != 0) {
                    updateOrderItemQuantity(conn, orderItemId, newQuantity);
                    recomputeOrderTotal(conn, orderId);
                }
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // remove one item from a PENDING order and return its units to stock
    public void removeOrderItem(int orderId, int orderItemId) throws SQLException {
        try (Connection conn = DBConnectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                requireEditableOrder(conn, orderId);
                OrderItem item = findOrderItemByIdForUpdate(conn, orderItemId);
                if (item == null || item.getOrderId() != orderId) {
                    throw new IllegalArgumentException("Order item does not belong to this order.");
                }

                adjustStock(conn, item.getTrackId(), item.getQuantity());
                deleteOrderItemRow(conn, orderItemId);
                recomputeOrderTotal(conn, orderId);
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // append a new track line to a PENDING order at the current database price
    public void addOrderItem(int orderId, int trackId, int quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }

        try (Connection conn = DBConnectionManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                requireEditableOrder(conn, orderId);

                Track track = findTrackByIdForUpdate(conn, trackId);
                if (track == null || !track.isActive()) {
                    throw new IllegalArgumentException("Track not available: " + trackId);
                }
                if (track.getPrice() == null) {
                    throw new IllegalArgumentException("Track price is missing for track " + track.getTitle());
                }
                if (!decrementStockIfAvailable(conn, track.getId(), quantity)) {
                    throw new IllegalArgumentException("Insufficient stock for track " + track.getTitle());
                }

                OrderItem item = new OrderItem();
                item.setOrderId(orderId);
                item.setTrackId(track.getId());
                item.setQuantity(quantity);
                item.setUnitPrice(track.getPrice());
                item.setLineTotal(track.getPrice().multiply(BigDecimal.valueOf(quantity)));
                insertOrderItems(conn, List.of(item));

                recomputeOrderTotal(conn, orderId);
                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // load the order inside the current transaction and verify it can still be modified
    private Order requireEditableOrder(Connection conn, int orderId) throws SQLException {
        Order order = findOrderByIdForUpdate(conn, orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new IllegalStateException("Only PENDING orders can be modified. Current status: " + order.getStatus());
        }
        return order;
    }

    private Order findOrderByIdForUpdate(Connection conn, int orderId) throws SQLException {
        String sql = "SELECT id, customer_id, user_id, order_date, status, total_amount, shipping_city FROM orders WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Order o = new Order();
                o.setId(rs.getInt("id"));
                o.setCustomerId(rs.getInt("customer_id"));
                o.setUserId(rs.getInt("user_id"));
                Timestamp ts = rs.getTimestamp("order_date");
                if (ts != null) {
                    o.setOrderDate(ts.toLocalDateTime());
                }
                o.setStatus(rs.getString("status"));
                o.setTotalAmount(rs.getBigDecimal("total_amount"));
                o.setShippingCity(rs.getString("shipping_city"));
                return o;
            }
        }
    }

    private OrderItem findOrderItemByIdForUpdate(Connection conn, int orderItemId) throws SQLException {
        String sql = "SELECT id, order_id, track_id, quantity, unit_price, line_total FROM order_items WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                OrderItem item = new OrderItem();
                item.setId(rs.getInt("id"));
                item.setOrderId(rs.getInt("order_id"));
                item.setTrackId(rs.getInt("track_id"));
                item.setQuantity(rs.getInt("quantity"));
                item.setUnitPrice(rs.getBigDecimal("unit_price"));
                item.setLineTotal(rs.getBigDecimal("line_total"));
                return item;
            }
        }
    }

    private Track findTrackByIdForUpdate(Connection conn, int trackId) throws SQLException {
        String sql = "SELECT id, title, artist, album, genre, price, stock_qty, is_active FROM tracks WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, trackId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // track not found
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

    // atomic conditional decrement: returns false instead of overselling when stock is insufficient,
    // so two concurrent checkouts can never both pass the same unit
    private boolean decrementStockIfAvailable(Connection conn, int trackId, int quantity) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = stock_qty - ? WHERE id = ? AND is_active = 1 AND stock_qty >= ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantity);
            ps.setInt(2, trackId);
            ps.setInt(3, quantity);
            return ps.executeUpdate() == 1;
        }
    }

    private void adjustStock(Connection conn, int trackId, int quantityToAdd) throws SQLException {
        String sql = "UPDATE tracks SET stock_qty = stock_qty + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantityToAdd);
            ps.setInt(2, trackId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Failed to update stock for track: " + trackId);
            }
        }
    }

    // return every reserved unit of an order back to the tracks in one statement
    private void restoreStockForOrder(Connection conn, int orderId) throws SQLException {
        String sql = "UPDATE tracks t JOIN order_items oi ON oi.track_id = t.id "
                + "SET t.stock_qty = t.stock_qty + oi.quantity WHERE oi.order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    private void updateOrderStatus(Connection conn, int orderId, String status) throws SQLException {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, orderId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Failed to update status for order: " + orderId);
            }
        }
    }

    private void updateOrderItemQuantity(Connection conn, int orderItemId, int newQuantity) throws SQLException {
        String sql = "UPDATE order_items SET quantity = ?, line_total = unit_price * ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newQuantity);
            ps.setInt(2, newQuantity);
            ps.setInt(3, orderItemId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Failed to update order item: " + orderItemId);
            }
        }
    }

    // keep the order header in sync with its lines after any item change
    private void recomputeOrderTotal(Connection conn, int orderId) throws SQLException {
        String sql = "UPDATE orders SET total_amount = (SELECT COALESCE(SUM(line_total), 0) FROM order_items WHERE order_id = ?) WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    private void deleteOrderItemRow(Connection conn, int orderItemId) throws SQLException {
        String sql = "DELETE FROM order_items WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderItemId);
            ps.executeUpdate();
        }
    }

    private void deleteOrderItems(Connection conn, int orderId) throws SQLException {
        String sql = "DELETE FROM order_items WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    private void deleteOrderRow(Connection conn, int orderId) throws SQLException {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Failed to delete order: " + orderId);
            }
        }
    }

    private int insertOrder(Connection conn, Order order) throws SQLException {
        String sql = "INSERT INTO orders (customer_id, user_id, order_date, status, total_amount, shipping_city) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, order.getCustomerId()); // set customer ID
            ps.setInt(2, order.getUserId()); // set user ID
            ps.setTimestamp(3, Timestamp.valueOf(order.getOrderDate())); // set order date as timestamp
            ps.setString(4, order.getStatus()); // set order status
            ps.setBigDecimal(5, order.getTotalAmount()); // set total amount
            ps.setString(6, order.getShippingCity()); // set shipping city
            ps.executeUpdate(); // execute insert statement

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1); // return generated order ID
                }
            }
        }
        throw new SQLException("Failed to create order");
    }

    private void insertOrderItems(Connection conn, List<OrderItem> items) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, track_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (OrderItem item : items) {
                ps.setInt(1, item.getOrderId()); // set order ID
                ps.setInt(2, item.getTrackId()); // set track ID
                ps.setInt(3, item.getQuantity()); // set quantity
                ps.setBigDecimal(4, item.getUnitPrice()); // set unit price
                ps.setBigDecimal(5, item.getLineTotal()); // set line total
                ps.addBatch(); // add to batch
            }
            ps.executeBatch(); // execute batch insert
        }
    }
}
