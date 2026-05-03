package com.example.musiclibrary;

import com.example.musiclibrary.dao.TrackDao;
import com.example.musiclibrary.model.Track;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class TrackDaoTest {
    private final TrackDao trackDao = new TrackDao();

    @Test // check whether all active tracks can be retrieved
    public void testFindAllActive() {
        try {
            List<Track> tracks = trackDao.findAllActive(); // only active tracks
            assertNotNull(tracks);
            assertTrue(tracks.size() > 0);
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // check whether a created track can be found by its ID
    public void testCreateAndFind() {
        try {
            Track track = new Track();
            track.setTitle("Test Track");
            track.setArtist("Test Artist");
            track.setAlbum("Test Album");
            track.setGenre("Test");
            track.setPrice(new BigDecimal("9.99"));
            track.setStockQty(10);

            int id = trackDao.create(track);
            assertTrue(id > 0); // check that an ID was returned

            Track found = trackDao.findById(id);
            assertNotNull(found);
            assertEquals("Test Track", found.getTitle()); // check if the title matches
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // check whether the track price is updated in the database
    public void testUpdate() {
        try {
            Track track = new Track();
            track.setTitle("Update Test");
            track.setArtist("Test Artist");
            track.setAlbum("Test Album");
            track.setGenre("Test");
            track.setPrice(new BigDecimal("5.00"));
            track.setStockQty(5);

            int id = trackDao.create(track);
            track.setId(id);
            track.setPrice(new BigDecimal("10.00"));

            trackDao.update(track);

            Track updated = trackDao.findById(id);
            assertEquals(new BigDecimal("10.00"), updated.getPrice()); // check if the price was updated
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }

    @Test // check whether the track becomes inactive after deletion
    public void testDelete() {
        try {
            Track track = new Track();
            track.setTitle("Delete Test");
            track.setArtist("Test Artist");
            track.setAlbum("Test Album");
            track.setGenre("Test");
            track.setPrice(new BigDecimal("1.00"));
            track.setStockQty(1);

            int id = trackDao.create(track);
            trackDao.delete(id);

            Track deleted = trackDao.findById(id);
            assertNotNull(deleted); // the track should still exist in the database, but it should be marked as inactive
            assertFalse(deleted.isActive());
        } catch (SQLException e) {
            fail("Database error: " + e.getMessage());
        }
    }
}