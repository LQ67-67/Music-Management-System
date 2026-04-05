package com.example.musiclibrary;

import com.example.musiclibrary.dao.OrderDao;
import com.example.musiclibrary.dao.OrderItemDao;
import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Order;
import com.example.musiclibrary.model.OrderItem;
import com.example.musiclibrary.model.Track;
import com.example.musiclibrary.service.OrderService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class OrderServiceTest {

    private final OrderService orderService = new OrderService();
    private final OrderDao orderDao = new OrderDao();
    private final OrderItemDao orderItemDao = new OrderItemDao();
    private final TrackDao trackDao = new TrackDao();

    @Test
    public void testCreateOrder() {
        try {
            // Create a test track
            Track track = new Track();
            track.setTitle("Order Test Track");
            track.setArtist("Test Artist");
            track.setAlbum("Test Album");
            track.setGenre("Test");
            track.setPrice(new BigDecimal("10.00"));
            track.setStockQty(20);
            int trackId = trackDao.create(track);

            // Create order items
            List<OrderItem> items = new ArrayList<>();
            OrderItem item = new OrderItem();
            item.setTrackId(trackId);
            item.setQuantity(2);
            item.setUnitPrice(new BigDecimal("10.00"));
            item.setLineTotal(new BigDecimal("20.00"));
            items.add(item);

            // Create order
            int orderId = orderService.createOrder(1, 1, items, "Test City");
            assertTrue(orderId > 0);

            // Verify order was created
            Order order = orderDao.findById(orderId);
            assertNotNull(order);
            assertEquals("PENDING", order.getStatus());

            // Clean up
            orderItemDao.deleteByOrder(orderId);
            orderDao.delete(orderId);
            trackDao.delete(trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test
    public void testCreateOrderWithInsufficientStock() {
        try {
            // Create a test track with low stock
            Track track = new Track();
            track.setTitle("Low Stock Track");
            track.setArtist("Test Artist");
            track.setAlbum("Test Album");
            track.setGenre("Test");
            track.setPrice(new BigDecimal("5.00"));
            track.setStockQty(1);
            int trackId = trackDao.create(track);

            // Create order items with more quantity than stock
            List<OrderItem> items = new ArrayList<>();
            OrderItem item = new OrderItem();
            item.setTrackId(trackId);
            item.setQuantity(5); // More than stock
            item.setUnitPrice(new BigDecimal("5.00"));
            item.setLineTotal(new BigDecimal("25.00"));
            items.add(item);

            // Should throw exception
            assertThrows(IllegalArgumentException.class, () -> {
                orderService.createOrder(1, 1, items, null);
            });

            // Clean up
            trackDao.delete(trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }
}