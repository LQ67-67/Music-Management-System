package com.example.musiclibrary.dao;

import com.example.musiclibrary.db.DBConnectionManager;
import com.example.musiclibrary.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CustomerDao {

    // Find all customers
    public List<Customer> findAll() throws SQLException {
        String sql = "SELECT id, name, email, phone, city FROM customers ORDER BY name";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        List<Customer> list = new ArrayList<>();
        while (rs.next()) {
            Customer customer = new Customer();
            customer.setId(rs.getInt("id"));
            customer.setName(rs.getString("name"));
            customer.setEmail(rs.getString("email"));
            customer.setPhone(rs.getString("phone"));
            customer.setCity(rs.getString("city"));
            list.add(customer);
        }

        rs.close();
        ps.close();
        conn.close();
        return list;
    }

    // Create new customer
    public int create(Customer customer) throws SQLException {
        String sql = "INSERT INTO customers (name, email, phone, city) VALUES (?, ?, ?, ?)";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);

        ps.setString(1, customer.getName());
        ps.setString(2, customer.getEmail());
        ps.setString(3, customer.getPhone());
        ps.setString(4, customer.getCity());

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

    // Update existing customer
    public void update(Customer customer) throws SQLException {
        String sql = "UPDATE customers SET name = ?, email = ?, phone = ?, city = ? WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);

        ps.setString(1, customer.getName());
        ps.setString(2, customer.getEmail());
        ps.setString(3, customer.getPhone());
        ps.setString(4, customer.getCity());
        ps.setInt(5, customer.getId());

        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    // Delete customer
    public void delete(int customerId) throws SQLException {
        String sql = "DELETE FROM customers WHERE id = ?";
        Connection conn = DBConnectionManager.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, customerId);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }
}