import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;

class MainTest {

    @Nested
    @DisplayName("isValidUrl")
    class IsValidUrlTests {
        @Test
        void returnsTrueForValidHttpUrl() {
            assertTrue(Main.isValidUrl("http://example.com"));
        }

        @Test
        void returnsTrueForValidHttpsUrl() {
            assertTrue(Main.isValidUrl("https://example.com"));
        }

        @Test
        void returnsTrueForUrlWithPathAndQuery() {
            assertTrue(Main.isValidUrl("https://example.com/path/to/page?query=1&foo=bar"));
        }

        @Test
        void returnsTrueForUrlWithPort() {
            assertTrue(Main.isValidUrl("https://example.com:8443/path"));
        }

        @Test
        void returnsFalseForNull() {
            assertFalse(Main.isValidUrl(null));
        }

        @Test
        void returnsFalseForEmptyString() {
            assertFalse(Main.isValidUrl(""));
        }

        @Test
        void returnsFalseForRelativeUrl() {
            assertFalse(Main.isValidUrl("/relative/path"));
        }

        @Test
        void returnsFalseForPlainText() {
            assertFalse(Main.isValidUrl("not a url"));
        }

        @Test
        void returnsTrueForFtpUrl() {
            assertTrue(Main.isValidUrl("ftp://files.example.com"));
        }
    }

    @Nested
    @DisplayName("generateId")
    class GenerateIdTests {
        @Test
        void returns8CharacterString() {
            var id = Main.generateId();
            assertNotNull(id);
            assertEquals(8, id.length());
        }

        @Test
        void returnsUniqueValuesOnSubsequentCalls() {
            var id1 = Main.generateId();
            var id2 = Main.generateId();
            assertNotEquals(id1, id2);
        }

        @Test
        void returnsOnlyAlphanumericCharacters() {
            var id = Main.generateId();
            assertTrue(id.matches("[a-f0-9]+"), "Expected hex characters, got: " + id);
        }
    }
}
