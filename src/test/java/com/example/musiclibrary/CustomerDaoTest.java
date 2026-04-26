package com.example.musiclibrary;

import com.example.musiclibrary.dao.CustomerDao;
import com.example.musiclibrary.model.Customer;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class CustomerDaoTest {
    private final CustomerDao customerDao = new CustomerDao();

    @Test
    public void testFindAll() {
        try {
            List<Customer> customers = customerDao.findAll();
            assertNotNull(customers);
            assertTrue(customers.size() > 0); // assume there are customers in the database
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test
    public void testCreate() {
        try {
            Customer customer = new Customer();
            customer.setName("Test Customer");
            customer.setEmail("test@test.com");
            customer.setPhone("1234567890");
            customer.setCity("Test City");

            int id = customerDao.create(customer);
            assertTrue(id > 0); // check that an ID was returned

            List<Customer> customers = customerDao.findAll();
            assertTrue(customers.stream().anyMatch(c -> c.getName().equals("Test Customer"))); // check that the new customer is in the list
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test
    public void testUpdate() {
        try {
            Customer customer = new Customer();
            customer.setName("Update Test");
            customer.setEmail("update@test.com");
            customer.setPhone("1111111111");
            customer.setCity("Old City");

            int id = customerDao.create(customer);
            customer.setId(id);
            customer.setCity("New City");

            customerDao.update(customer);

            List<Customer> customers = customerDao.findAll();
            Customer updated = customers.stream().filter(c -> c.getId() == id).findFirst().orElse(null); // find the updated customer
            assertNotNull(updated);
            assertEquals("New City", updated.getCity());
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test
    public void testDelete() {
        try {
            Customer customer = new Customer();
            customer.setName("Delete Test");
            customer.setEmail("delete@test.com");
            customer.setPhone("9999999999");
            customer.setCity("Delete City");

            int id = customerDao.create(customer);
            customerDao.delete(id);

            List<Customer> customers = customerDao.findAll();
            assertFalse(customers.stream().anyMatch(c -> c.getId() == id));
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }
}