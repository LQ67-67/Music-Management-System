package com.example.musiclibrary;

import com.example.musiclibrary.dao.UserDao;
import com.example.musiclibrary.model.User;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

public class UserDaoTest {

    private final UserDao userDao = new UserDao();

    @Test // check whether the admin user can be found by username
    public void testFindByUsername() {
        try {
            User user = userDao.findByUsername("admin"); // new admin
            assertNotNull(user);
            assertEquals("ADMIN", user.getRole()); // check that the admin user has the correct role
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // check whether a new user can be created and whether the role is correct
    public void testCreate() {
        try {
            String username = "testuser_" + System.currentTimeMillis();
            int id = userDao.create(username, "testpass", "USER"); // new user and get the ID
            assertTrue(id > 0);

            User user = userDao.findByUsername(username);
            assertNotNull(user);
            assertEquals("USER", user.getRole()); // check that the new user has the correct role
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // check whether the system throws an error when a duplicate username is used
    public void testCreateDuplicateUsername() {
        try {
            String duplicateUsername = "duplicate_" + System.currentTimeMillis();
            userDao.create(duplicateUsername, "pass1", "USER"); // user
            assertThrows(SQLException.class, () -> {
                userDao.create(duplicateUsername, "pass2", "USER"); // attempt to create another user with the same username, should throw an exception
            });
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }
}