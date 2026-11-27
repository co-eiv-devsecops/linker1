package linker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class LinkRepositoryTest {

    Connection conn;
    LinkRepository repo;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }
        repo = new LinkRepository(conn);
        repo.ensureClicksColumn();
    }

    @Test
    void findUrlByIdReturnsUrlWhenIdExists() throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
            ps.setString(1, "abc12345");
            ps.setString(2, "https://example.com");
            ps.executeUpdate();
        }

        assertEquals("https://example.com", repo.findUrlById("abc12345"));
    }

    @Test
    void findUrlByIdReturnsNullWhenIdDoesNotExist() throws SQLException {
        assertNull(repo.findUrlById("missing1"));
    }

    @Test
    void findIdByUrlReturnsIdWhenUrlExists() throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
            ps.setString(1, "abc12345");
            ps.setString(2, "https://example.com");
            ps.executeUpdate();
        }

        assertEquals("abc12345", repo.findIdByUrl("https://example.com"));
    }

    @Test
    void findIdByUrlReturnsNullWhenUrlUnknown() throws SQLException {
        assertNull(repo.findIdByUrl("https://unknown.example.com"));
    }

    @Test
    void insertShortUrlPersistsRowAndReturnsGeneratedId() throws SQLException {
        var id = repo.insertShortUrl("https://example.com");

        assertNotNull(id);
        assertEquals("https://example.com", repo.findUrlById(id));
    }

    @Test
    void insertShortUrlGeneratesDifferentIdsForDifferentUrls() throws SQLException {
        var firstId = repo.insertShortUrl("https://example.com");
        var secondId = repo.insertShortUrl("https://other.example.com");

        assertNotEquals(firstId, secondId);
    }

    @Test
    void findUrlByIdThrowsExceptionWhenConnectionClosed() throws SQLException {
        conn.close();
        assertThrows(SQLException.class, () -> repo.findUrlById("id"));
    }

    @Test
    void findIdByUrlThrowsExceptionWhenConnectionClosed() throws SQLException {
        conn.close();
        assertThrows(SQLException.class, () -> repo.findIdByUrl("url"));
    }

    @Test
    void countLinksReturnsZeroWhenTableEmpty() throws SQLException {
        assertEquals(0, repo.countLinks());
    }

    @Test
    void countLinksReturnsNumberOfStoredRows() throws SQLException {
        repo.insertShortUrl("https://example.com");
        repo.insertShortUrl("https://other.example.com");

        assertEquals(2, repo.countLinks());
    }

    @Test
    void deleteRemovesRowAndReturnsTrueWhenIdExists() throws SQLException {
        var id = repo.insertShortUrl("https://example.com");
        assertEquals(1, repo.countLinks());

        assertTrue(repo.delete(id));
        assertEquals(0, repo.countLinks());
        assertNull(repo.findUrlById(id));
    }

    @Test
    void deleteReturnsFalseWhenIdDoesNotExist() throws SQLException {
        assertFalse(repo.delete("doesnotexist"));
    }

    @Test
    void deleteThrowsExceptionWhenConnectionClosed() throws SQLException {
        conn.close();
        assertThrows(SQLException.class, () -> repo.delete("id"));
    }

    @Test
    void ensureClicksColumnMigratesLegacyTableKeepingRows() throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE shorturl");
            stmt.execute("CREATE TABLE shorturl (id TEXT PRIMARY KEY, url TEXT)");
            stmt.execute("INSERT INTO shorturl (id, url) VALUES ('legacy01', 'https://example.com')");
        }

        repo.ensureClicksColumn();

        assertEquals("https://example.com", repo.findUrlById("legacy01"));
        assertEquals(0L, (long) repo.findClicksById("legacy01"));
    }

    @Test
    void ensureClicksColumnIsIdempotent() throws SQLException {
        repo.ensureClicksColumn();
        repo.ensureClicksColumn();
        assertEquals(0L, (long) repo.findClicksById(repo.insertShortUrl("https://example.com")));
    }

    @Test
    void incrementClicksAddsOnePerCallAndReturnsTrue() throws SQLException {
        var id = repo.insertShortUrl("https://example.com");

        assertTrue(repo.incrementClicks(id));
        assertTrue(repo.incrementClicks(id));

        assertEquals(2L, (long) repo.findClicksById(id));
    }

    @Test
    void incrementClicksReturnsFalseWhenIdMissing() throws SQLException {
        assertFalse(repo.incrementClicks("missing1"));
    }

    @Test
    void findClicksByIdReturnsNullWhenIdMissing() throws SQLException {
        assertNull(repo.findClicksById("missing1"));
    }
}