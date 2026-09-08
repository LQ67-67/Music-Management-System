package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Customer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class CustomerDao {

    // find all customers
    public List<Customer> findAll() throws SQLException {
        String sql = "SELECT id, user_id, name, email, phone, city FROM customers ORDER BY name";
        try (var conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Customer> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        }
    }

    // find the customer linked to a login user (user_id = 0 means unlinked legacy rows)
    public Customer findByUserId(int userId) throws SQLException {
        String sql = "SELECT id, user_id, name, email, phone, city FROM customers WHERE user_id = ?";
        try (var conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // create new customer; links it to a login user when userId is set
    public int create(Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (user_id, name, email, phone, city) VALUES (?, ?, ?, ?, ?)";
        try (var conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customer.getUserId());
            ps.setString(2, customer.getName());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getPhone());
            ps.setString(5, customer.getCity());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    // update existing customer (name, contact fields only; id and user_id are immutable here)
    public void update(Customer customer) throws SQLException {
        String sql = "UPDATE customers SET name = ?, email = ?, phone = ?, city = ? WHERE id = ?";
        try (var conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getEmail());
            ps.setString(3, customer.getPhone());
            ps.setString(4, customer.getCity());
            ps.setInt(5, customer.getId());
            ps.executeUpdate();
        }
    }

    // link an existing (legacy, unlinked) customer row to a login user
    public void linkToUser(int customerId, int userId) throws SQLException {
        String sql = "UPDATE customers SET user_id = ? WHERE id = ?";
        try (var conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    // delete customer; orders and order items are removed by database cascade
    public void delete(int customerId) throws SQLException {
        String sql = "DELETE FROM customers WHERE id = ?";
        try (var conn = DBConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.executeUpdate();
        }
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setId(rs.getInt("id"));
        customer.setUserId(rs.getInt("user_id"));
        customer.setName(rs.getString("name"));
        customer.setEmail(rs.getString("email"));
        customer.setPhone(rs.getString("phone"));
        customer.setCity(rs.getString("city"));
        return customer;
    }
}
