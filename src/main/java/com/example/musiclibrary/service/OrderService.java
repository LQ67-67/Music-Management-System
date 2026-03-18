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

    private final OrderDao orderDao = new OrderDao();
    private final OrderItemDao orderItemDao = new OrderItemDao();
    private final TrackDao trackDao = new TrackDao();

    public Order createOrder(int customerId, int userId, List<OrderItem> cartItems, String shippingCity) throws SQLException {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        try (Connection conn = DBConnectionManager.getConnection()) {
            try {
                conn.setAutoCommit(false);

                BigDecimal total = BigDecimal.ZERO;
                List<OrderItem> itemsToInsert = new ArrayList<>();

                for (OrderItem item : cartItems) {
                    Track track = trackDao.findById(conn, item.getTrackId());
                    if (track == null || !track.isActive()) {
                        throw new SQLException("Track not available: " + item.getTrackId());
                    }
                    if (item.getQuantity() <= 0 || item.getQuantity() > track.getStockQty()) {
                        throw new SQLException("Invalid quantity for track " + track.getTitle());
                    }
                    BigDecimal lineTotal = track.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                    item.setUnitPrice(track.getPrice());
                    item.setLineTotal(lineTotal);
                    total = total.add(lineTotal);
                    itemsToInsert.add(item);

                    int newStock = track.getStockQty() - item.getQuantity();
                    trackDao.updateStock(conn, track.getId(), newStock);
                }

                Order order = new Order();
                order.setCustomerId(customerId);
                order.setUserId(userId);
                order.setOrderDate(LocalDateTime.now());
                order.setStatus("PENDING");
                order.setTotalAmount(total);
                order.setShippingCity(shippingCity);

                int orderId = orderDao.insert(conn, order);
                order.setId(orderId);

                for (OrderItem item : itemsToInsert) {
                    item.setOrderId(orderId);
                }
                orderItemDao.insertBatch(conn, itemsToInsert);

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
}

