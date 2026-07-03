package linker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class LinkServiceAliasTest {

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
    @DisplayName("isValidAlias")
    class IsValidAliasTests {
        @Test
        void acceptsSimpleAlphanumeric() {
            assertTrue(LinkService.isValidAlias("mylink1"));
        }

        @Test
        void acceptsHyphensAndUnderscores() {
            assertTrue(LinkService.isValidAlias("my-link_1"));
        }

        @Test
        void rejectsNull() {
            assertFalse(LinkService.isValidAlias(null));
        }

        @Test
        void rejectsEmptyString() {
            assertFalse(LinkService.isValidAlias(""));
        }

        @Test
        void rejectsSlash() {
            assertFalse(LinkService.isValidAlias("foo/bar"));
        }

        @Test
        void rejectsWhitespace() {
            assertFalse(LinkService.isValidAlias("my alias"));
        }

        @Test
        void rejectsTooLong() {
            assertFalse(LinkService.isValidAlias("a".repeat(65)));
        }

        @Test
        void acceptsMaxLength() {
            assertTrue(LinkService.isValidAlias("a".repeat(64)));
        }

        @Test
        void rejectsReservedWordLink() {
            assertFalse(LinkService.isValidAlias("link"));
        }

        @Test
        void rejectsReservedWordAppJs() {
            assertFalse(LinkService.isValidAlias("app.js"));
        }

        @Test
        void rejectsReservedWordStylesCss() {
            assertFalse(LinkService.isValidAlias("styles.css"));
        }
    }

    @Nested
    @DisplayName("create with alias")
    class CreateWithAliasTests {
        @Test
        void usesAliasAsId() throws SQLException {
            var link = service.create("https://example.com", "mylink");

            assertEquals("mylink", link.id());
            assertEquals("https://example.com", repo.findUrlById("mylink"));
        }

        @Test
        void fallsBackToGeneratedIdWhenAliasNull() throws SQLException {
            var link = service.create("https://example.com", null);

            assertEquals(8, link.id().length());
        }

        @Test
        void throwsAliasConflictWhenAliasTaken() throws SQLException {
            service.create("https://example.com", "taken");

            assertThrows(AliasConflictException.class,
                    () -> service.create("https://other.com", "taken"));
        }

        @Test
        void throwsIllegalArgumentForInvalidAlias() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("https://example.com", "has space"));
        }

        @Test
        void repeatingSameUrlAndAliasIsIdempotent() throws SQLException {
            var first = service.create("https://example.com", "mylink");
            var second = service.create("https://example.com", "mylink");

            assertEquals(first.id(), second.id());
        }

        @Test
        void throwsIllegalArgumentForInvalidUrlEvenWithValidAlias() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.create("not a url", "mylink"));
        }
    }
}
