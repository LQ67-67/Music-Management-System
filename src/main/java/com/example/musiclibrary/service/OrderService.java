package com.example.musiclibrary.service;

import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.OrderItemDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.musiclibrary.db.DBConnectionManager;

public class OrderService {

    private OrderDao orderDao;
    private OrderItemDao orderItemDao;
    private TrackDao trackDao;

    public OrderService() {
        orderDao = new OrderDao();
        orderItemDao = new OrderItemDao();
        trackDao = new TrackDao();
    }

    // Create a new order with cart items
    public Order createOrder(int customerId, int userId, List<OrderItem> cartItems, String shippingCity) throws SQLException {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        Connection conn = DBConnectionManager.getConnection();
        Order order = null;

        try {
            // Start transaction
            conn.setAutoCommit(false);

            BigDecimal total = BigDecimal.ZERO;
            List<OrderItem> itemsToInsert = new ArrayList<>();

            // Process each cart item
            for (OrderItem item : cartItems) {
                Track track = trackDao.findById(item.getTrackId());

                // Validate track
                if (track == null || !track.isActive()) {
                    throw new SQLException("Track not available: " + item.getTrackId());
                }

                // Validate quantity
                if (item.getQuantity() <= 0 || item.getQuantity() > track.getStockQty()) {
                    throw new SQLException("Invalid quantity for track " + track.getTitle());
                }

                // Calculate line total
                BigDecimal lineTotal = track.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                item.setUnitPrice(track.getPrice());
                item.setLineTotal(lineTotal);
                total = total.add(lineTotal);
                itemsToInsert.add(item);

                // Update stock
                int newStock = track.getStockQty() - item.getQuantity();
                trackDao.updateStock(track.getId(), newStock);
            }

            // Create order
            order = new Order();
            order.setCustomerId(customerId);
            order.setUserId(userId);
            order.setOrderDate(LocalDateTime.now());
            order.setStatus("PENDING");
            order.setTotalAmount(total);
            order.setShippingCity(shippingCity);

            int orderId = orderDao.insert(order);
            order.setId(orderId);

            // Insert order items
            for (OrderItem item : itemsToInsert) {
                item.setOrderId(orderId);
            }
            orderItemDao.insertBatch(itemsToInsert);

            // Commit transaction
            conn.commit();
            return order;
        } catch (SQLException | RuntimeException ex) {
            // Rollback on error
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
            conn.close();
        }
    }
}