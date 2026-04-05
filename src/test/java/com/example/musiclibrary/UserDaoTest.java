package com.example.musiclibrary;

import com.example.musiclibrary.dao.UserDao;
import com.example.musiclibrary.model.User;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

public class UserDaoTest {

    private final UserDao userDao = new UserDao();

    @Test
    public void testFindByUsername() {
        try {
            User user = userDao.findByUsername("admin");
            assertNotNull(user);
            assertEquals("ADMIN", user.getRole());
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test
    public void testCreate() {
        try {
            String username = "testuser_" + System.currentTimeMillis();
            int id = userDao.create(username, "testpass", "USER");
            assertTrue(id > 0);

            User user = userDao.findByUsername(username);
            assertNotNull(user);
            assertEquals("USER", user.getRole());
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test
    public void testCreateDuplicateUsername() {
        try {
            userDao.create("duplicate", "pass1", "USER");
            assertThrows(SQLException.class, () -> {
                userDao.create("duplicate", "pass2", "USER");
            });
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }
}