package com.example.musiclibrary.util;

import com.example.musiclibrary.model.OrderItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Persists the shopping cart between application restarts, one file per user
 * under the home directory: ~/.music-library-cart-<userId>.properties
 */
public final class CartStore {

    private CartStore() {
    }

    private static Path fileFor(int userId) {
        return Path.of(System.getProperty("user.home"), ".music-library-cart-" + userId + ".properties");
    }

    public static void save(int userId, List<OrderItem> items) {
        var props = new Properties();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            props.setProperty(i + ".trackId", String.valueOf(item.getTrackId()));
            props.setProperty(i + ".quantity", String.valueOf(item.getQuantity()));
            props.setProperty(i + ".unitPrice", item.getUnitPrice() == null ? "0" : item.getUnitPrice().toPlainString());
        }
        try (OutputStream out = Files.newOutputStream(fileFor(userId))) {
            props.store(out, "Music Library cart for user " + userId);
        } catch (IOException e) {
            // cart persistence is best-effort; losing it must never block checkout
            System.err.println("Failed to save cart: " + e.getMessage());
        }
    }

    public static List<OrderItem> load(int userId) {
        Path file = fileFor(userId);
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        var props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("Failed to load cart: " + e.getMessage());
            return new ArrayList<>();
        }

        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; props.containsKey(i + ".trackId"); i++) {
            OrderItem item = new OrderItem();
            item.setTrackId(Integer.parseInt(props.getProperty(i + ".trackId")));
            item.setQuantity(Integer.parseInt(props.getProperty(i + ".quantity", "1")));
            item.setUnitPrice(new BigDecimal(props.getProperty(i + ".unitPrice", "0")));
            item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            items.add(item);
        }
        return items;
    }

    public static void clear(int userId) {
        try {
            Files.deleteIfExists(fileFor(userId));
        } catch (IOException e) {
            System.err.println("Failed to clear cart: " + e.getMessage());
        }
    }
}
