package linker.routes;

import io.javalin.Javalin;
import linker.LinkRepository;
import linker.LinkService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class LinkRoutesTest {

    static Javalin app;
    static int port;
    static Connection conn;
    static HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @BeforeAll
    static void startServer() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }
        try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
            ps.setString(1, "existing");
            ps.setString(2, "https://example.com");
            ps.executeUpdate();
        }

        var repo = new LinkRepository(conn);
        var service = new LinkService(repo);
        app = Javalin.create().start(0);
        LinkRoutes.register(app, service);
        port = app.port();
    }

    @AfterAll
    static void stopServer() throws SQLException {
        app.stop();
        conn.close();
    }

    @Test
    void getExistingIdRedirectsToStoredUrl() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/existing")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(301, response.statusCode());
        assertEquals("https://example.com", response.headers().firstValue("Location").orElse(null));
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/does-not-exist")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }
}
