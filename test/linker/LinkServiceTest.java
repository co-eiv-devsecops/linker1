package linker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class LinkServiceTest {

    Connection conn;
    LinkRepository repo;
    LinkService service;

    @BeforeEach
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }
        repo = new LinkRepository(conn);
        service = new LinkService(repo);
    }

    @Nested
    @DisplayName("get")
    class GetTests {
        @Test
        void returnsUrlForExistingId() throws SQLException {
            try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
                ps.setString(1, "abc12345");
                ps.setString(2, "https://example.com");
                ps.executeUpdate();
            }

            assertEquals("https://example.com", service.get("abc12345"));
        }

        @Test
        void returnsNullForUnknownId() throws SQLException {
            assertNull(service.get("missing1"));
        }
    }

    @Nested
    @DisplayName("isValidUrl")
    class IsValidUrlTests {
        @Test
        void returnsTrueForValidHttpUrl() {
            assertTrue(LinkService.isValidUrl("http://example.com"));
        }

        @Test
        void returnsTrueForValidHttpsUrl() {
            assertTrue(LinkService.isValidUrl("https://example.com"));
        }

        @Test
        void returnsTrueForUrlWithPathAndQuery() {
            assertTrue(LinkService.isValidUrl("https://example.com/path/to/page?query=1&foo=bar"));
        }

        @Test
        void returnsTrueForUrlWithPort() {
            assertTrue(LinkService.isValidUrl("https://example.com:8443/path"));
        }

        @Test
        void returnsFalseForNull() {
            assertFalse(LinkService.isValidUrl(null));
        }

        @Test
        void returnsFalseForEmptyString() {
            assertFalse(LinkService.isValidUrl(""));
        }

        @Test
        void returnsFalseForRelativeUrl() {
            assertFalse(LinkService.isValidUrl("/relative/path"));
        }

        @Test
        void returnsFalseForPlainText() {
            assertFalse(LinkService.isValidUrl("not a url"));
        }

        @Test
        void returnsTrueForFtpUrl() {
            assertTrue(LinkService.isValidUrl("ftp://files.example.com"));
        }
    }

    @Nested
    @DisplayName("generateId")
    class GenerateIdTests {
        @Test
        void returns8CharacterString() {
            var id = LinkService.generateId();
            assertNotNull(id);
            assertEquals(8, id.length());
        }

        @Test
        void returnsUniqueValuesOnSubsequentCalls() {
            var id1 = LinkService.generateId();
            var id2 = LinkService.generateId();
            assertNotEquals(id1, id2);
        }

        @Test
        void returnsOnlyAlphanumericCharacters() {
            var id = LinkService.generateId();
            assertTrue(id.matches("[a-f0-9]+"), "Expected hex characters, got: " + id);
        }
    }
}
