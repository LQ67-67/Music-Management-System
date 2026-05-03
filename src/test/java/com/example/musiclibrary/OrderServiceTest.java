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

    @Test // check whether an order can be created successfully and whether its status is `PENDING`
    public void testCreateOrder() {
        try {
            // test track
            Track track = new Track();
            track.setTitle("Order Test Track");
            track.setArtist("Test Artist");
            track.setAlbum("Test Album");
            track.setGenre("Test");
            track.setPrice(new BigDecimal("10.00"));
            track.setStockQty(20);
            int trackId = trackDao.create(track); // create track and get ID

            // order items
            List<OrderItem> items = new ArrayList<>();
            OrderItem item = new OrderItem();
            item.setTrackId(trackId);
            item.setQuantity(2);
            item.setUnitPrice(new BigDecimal("10.00"));
            item.setLineTotal(new BigDecimal("20.00"));
            items.add(item);

            // order
            Order createdOrder = orderService.createOrder(1, 1, items, "Test City");
            assertNotNull(createdOrder);
            assertTrue(createdOrder.getId() > 0);

            // verify order was created or not
            Order order = orderDao.findById(createdOrder.getId());
            assertNotNull(order);
            assertEquals("PENDING", order.getStatus());

            // Clean up
            orderItemDao.deleteByOrder(createdOrder.getId());
            orderDao.delete(createdOrder.getId());
            trackDao.delete(trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // check whether the system rejects an order when stock is insufficient
    public void testCreateOrderWithInsufficientStock() {
        try {
            // create test track with low stock
            Track track = new Track();
            track.setTitle("Low Stock Track");
            track.setArtist("Test Artist");
            track.setAlbum("Test Album");
            track.setGenre("Test");
            track.setPrice(new BigDecimal("5.00"));
            track.setStockQty(1);
            int trackId = trackDao.create(track);

            // create order items with more quantity than stock
            List<OrderItem> items = new ArrayList<>();
            OrderItem item = new OrderItem();
            item.setTrackId(trackId);
            item.setQuantity(5); // More than stock
            item.setUnitPrice(new BigDecimal("5.00"));
            item.setLineTotal(new BigDecimal("25.00"));
            items.add(item);

            assertThrows(IllegalArgumentException.class, () -> {
                orderService.createOrder(1, 1, items, null); // should throw exception
            });
            trackDao.delete(trackId); // clean up
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }
}