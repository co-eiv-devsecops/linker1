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
}
