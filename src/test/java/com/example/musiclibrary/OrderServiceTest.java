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

    @Test // adjusting an item's quantity must move stock accordingly and recompute the order total
    public void testAdjustOrderItemQuantity() {
        try {
            int trackId = trackDao.create(testTrack("Adjust Qty Track", 10));

            Order order = orderService.createOrder(1, 1, List.of(cartItem(trackId, 4)), "Test City");
            int itemId = orderItemDao.findByOrder(order.getId()).get(0).getId();

            assertEquals(6, trackDao.findById(trackId).getStockQty()); // 10 - 4

            orderService.adjustOrderItemQuantity(order.getId(), itemId, 9); // +5 more
            assertEquals(1, trackDao.findById(trackId).getStockQty());
            assertEquals(new BigDecimal("89.10"), orderDao.findById(order.getId()).getTotalAmount()); // 9 * 9.90

            orderService.adjustOrderItemQuantity(order.getId(), itemId, 2); // release 7 back
            assertEquals(8, trackDao.findById(trackId).getStockQty());

            // requesting more than available stock must fail without any side effect
            assertThrows(IllegalArgumentException.class,
                    () -> orderService.adjustOrderItemQuantity(order.getId(), itemId, 20));
            assertEquals(8, trackDao.findById(trackId).getStockQty());
            assertEquals(2, orderItemDao.findByOrder(order.getId()).get(0).getQuantity());

            cleanupOrder(order.getId(), trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // removing an item returns its units to stock and empties the order total
    public void testRemoveOrderItem() {
        try {
            int trackId = trackDao.create(testTrack("Remove Item Track", 5));
            Order order = orderService.createOrder(1, 1, List.of(cartItem(trackId, 3)), "Test City");
            int itemId = orderItemDao.findByOrder(order.getId()).get(0).getId();

            orderService.removeOrderItem(order.getId(), itemId);

            assertEquals(5, trackDao.findById(trackId).getStockQty()); // all units returned
            assertTrue(orderItemDao.findByOrder(order.getId()).isEmpty());
            assertEquals(0, orderDao.findById(order.getId()).getTotalAmount().compareTo(BigDecimal.ZERO));

            cleanupOrder(order.getId(), trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // adding an item to a PENDING order consumes stock at the current price
    public void testAddOrderItem() {
        try {
            int trackId = trackDao.create(testTrack("Add Item Track", 4));
            Order order = orderService.createOrder(1, 1, List.of(cartItem(trackId, 2)), "Test City");

            orderService.addOrderItem(order.getId(), trackId, 1);

            assertEquals(1, trackDao.findById(trackId).getStockQty());
            assertEquals(2, orderItemDao.findByOrder(order.getId()).size());
            assertEquals(new BigDecimal("29.70"), orderDao.findById(order.getId()).getTotalAmount()); // 2 + 1 units

            // stock is exhausted — a further add must be rejected
            assertThrows(IllegalArgumentException.class,
                    () -> orderService.addOrderItem(order.getId(), trackId, 5));

            cleanupOrder(order.getId(), trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // cancelling an order must restore the reserved stock and mark it CANCELLED
    public void testCancelOrderRestoresStock() {
        try {
            int trackId = trackDao.create(testTrack("Cancel Track", 10));
            Order order = orderService.createOrder(1, 1, List.of(cartItem(trackId, 4)), "Test City");
            assertEquals(6, trackDao.findById(trackId).getStockQty());

            orderService.cancelOrder(order.getId());

            Order cancelled = orderDao.findById(order.getId());
            assertEquals("CANCELLED", cancelled.getStatus());
            assertEquals(10, trackDao.findById(trackId).getStockQty());

            // cancelling again is a no-op, cancelling a PAID order is refused
            assertDoesNotThrow(() -> orderService.cancelOrder(order.getId()));
            Order paid = orderService.createOrder(1, 1, List.of(cartItem(trackId, 1)), null);
            orderDao.updateStatusAndCity(paid.getId(), "PAID", null);
            assertThrows(IllegalStateException.class, () -> orderService.cancelOrder(paid.getId()));

            cleanupOrder(order.getId(), trackId);
            cleanupOrder(paid.getId(), trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // deleting a live order returns stock and removes the rows; PAID orders are protected
    public void testDeleteOrder() {
        try {
            int trackId = trackDao.create(testTrack("Delete Track", 7));
            Order order = orderService.createOrder(1, 1, List.of(cartItem(trackId, 2)), "Test City");

            orderService.deleteOrder(order.getId());

            assertNull(orderDao.findById(order.getId()));
            assertTrue(orderItemDao.findByOrder(order.getId()).isEmpty());
            assertEquals(7, trackDao.findById(trackId).getStockQty());

            Order paid = orderService.createOrder(1, 1, List.of(cartItem(trackId, 1)), null);
            orderDao.updateStatusAndCity(paid.getId(), "PAID", null);
            assertThrows(IllegalStateException.class, () -> orderService.deleteOrder(paid.getId()));
            assertNotNull(orderDao.findById(paid.getId()));

            cleanupOrder(paid.getId(), trackId);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    // ---- helpers ----

    private Track testTrack(String title, int stock) {
        Track track = new Track();
        track.setTitle(title);
        track.setArtist("Test Artist");
        track.setAlbum("Test Album");
        track.setGenre("Test");
        track.setPrice(new BigDecimal("9.90"));
        track.setStockQty(stock);
        return track;
    }

    private OrderItem cartItem(int trackId, int quantity) {
        OrderItem item = new OrderItem();
        item.setTrackId(trackId);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal("9.90"));
        item.setLineTotal(new BigDecimal("9.90").multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private void cleanupOrder(int orderId, int trackId) throws SQLException {
        orderItemDao.deleteByOrder(orderId);
        orderDao.delete(orderId);
        trackDao.delete(trackId);
    }
}