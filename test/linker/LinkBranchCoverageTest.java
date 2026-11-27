package linker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Covers every branch of LinkService and LinkRepository (used by the SonarCloud coverage gate). */
class LinkBranchCoverageTest {

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
        repo.ensureClicksColumn();
        service = new LinkService(repo);
    }

    // ---- isValidUrl: each operand of the && chain ----

    @Test
    void urlWithNonHttpSchemeIsInvalid() {
        assertFalse(LinkService.isValidUrl("ftp://example.com/file"));
    }

    @Test
    void urlWithoutSchemeIsInvalid() {
        assertFalse(LinkService.isValidUrl("example.com"));
    }

    @Test
    void urlWithoutHostIsInvalid() {
        assertFalse(LinkService.isValidUrl("http:/path-only"));
        assertFalse(LinkService.isValidUrl("mailto:someone@example.com"));
    }

    @Test
    void urlWithPortAboveRangeIsInvalid() {
        assertFalse(LinkService.isValidUrl("http://example.com:99999/x"));
    }

    @Test
    void urlWithUpperCaseSchemeIsValid() {
        assertTrue(LinkService.isValidUrl("HTTPS://example.com"));
        assertTrue(LinkService.isValidUrl("HTTP://example.com"));
    }

    @Test
    void malformedUrlIsInvalid() {
        assertFalse(LinkService.isValidUrl("http://exa mple.com"));
    }

    // ---- isValidAlias: each early return ----

    @Test
    void aliasValidationBranches() {
        assertFalse(LinkService.isValidAlias(null));
        assertFalse(LinkService.isValidAlias(""));
        assertFalse(LinkService.isValidAlias("a".repeat(65)));
        assertFalse(LinkService.isValidAlias("bad alias!"));
        assertFalse(LinkService.isValidAlias("Link"));
        assertTrue(LinkService.isValidAlias("ok-alias_1"));
    }

    // ---- create / createResult ----

    @Test
    void createResultRejectsInvalidUrl() {
        assertThrows(IllegalArgumentException.class, () -> service.createResult("nope"));
    }

    @Test
    void createResultReusesExistingLink() throws SQLException {
        var first = service.createResult("https://example.com/a");
        var second = service.createResult("https://example.com/a");
        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.link().id(), second.link().id());
        assertEquals(first.link().id(), service.create("https://example.com/a").id());
    }

    @Test
    void createWithAliasRejectsInvalidUrl() {
        assertThrows(IllegalArgumentException.class, () -> service.create("nope", "alias1"));
    }

    @Test
    void createWithNullAliasFallsBackToGeneratedId() throws SQLException {
        var link = service.create("https://example.com/n", null);
        assertEquals(8, link.id().length());
    }

    @Test
    void createWithInvalidAliasIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create("https://example.com/b", "bad alias"));
    }

    @Test
    void createWithFreeAliasStoresIt() throws SQLException {
        var link = service.create("https://example.com/c", "my-alias");
        assertEquals("my-alias", link.id());
        assertEquals("https://example.com/c", service.get("my-alias"));
    }

    @Test
    void createWithSameAliasAndSameUrlIsIdempotent() throws SQLException {
        service.create("https://example.com/d", "same");
        var again = service.create("https://example.com/d", "same");
        assertEquals("same", again.id());
    }

    @Test
    void createWithAliasInUseForOtherUrlThrowsConflict() throws SQLException {
        service.create("https://example.com/e", "taken");
        assertThrows(AliasConflictException.class, () -> service.create("https://example.com/f", "taken"));
    }

    // ---- get / delete / open / clicks ----

    @Test
    void getAndDeleteReflectExistence() throws SQLException {
        assertNull(service.get("missing1"));
        assertFalse(service.delete("missing1"));
        service.create("https://example.com/g", "to-delete");
        assertTrue(service.delete("to-delete"));
        assertNull(service.get("to-delete"));
    }

    @Test
    void openCountsClicksAndReturnsNullWhenMissing() throws SQLException {
        assertNull(service.open("missing1"));
        assertNull(service.getClicks("missing1"));
        service.create("https://example.com/h", "counted");
        assertEquals("https://example.com/h", service.open("counted"));
        assertEquals("https://example.com/h", service.open("counted"));
        assertEquals(Long.valueOf(2L), service.getClicks("counted"));
    }

    // ---- repository branches ----

    @Test
    void repositoryLookupsReturnNullWhenMissingAndValueWhenPresent() throws SQLException {
        assertNull(repo.findIdByUrl("https://nope.example"));
        assertNull(repo.findClicksById("missing1"));
        assertFalse(repo.incrementClicks("missing1"));
        assertFalse(repo.delete("missing1"));
        assertEquals(0L, repo.countLinks());

        repo.insertShortUrlWithId("abc", "https://example.com/i");
        assertEquals("abc", repo.findIdByUrl("https://example.com/i"));
        assertEquals(Long.valueOf(0L), repo.findClicksById("abc"));
        assertTrue(repo.incrementClicks("abc"));
        assertEquals(Long.valueOf(1L), repo.findClicksById("abc"));
        assertEquals(1L, repo.countLinks());
        assertTrue(repo.delete("abc"));
    }

    @Test
    void ensureClicksColumnSkipsMigrationWhenColumnAlreadyExists() throws SQLException {
        repo.ensureClicksColumn();
        repo.insertShortUrlWithId("z", "https://example.com/z");
        assertEquals(Long.valueOf(0L), repo.findClicksById("z"));
    }
}
