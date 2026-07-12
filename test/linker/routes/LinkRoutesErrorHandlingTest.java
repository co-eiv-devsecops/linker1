package linker.routes;

import io.javalin.Javalin;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import linker.LinkRepository;
import linker.LinkService;
import linker.telemetry.LinkSpans;
import linker.telemetry.RequestMetrics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Covers the unexpected-failure (HTTP 500) branches in LinkRoutes: the
 * connection is closed before the server starts, so every repository call
 * throws SQLException and the routes' catch-all error handling runs for real.
 */
class LinkRoutesErrorHandlingTest {

    static Javalin app;
    static int port;
    static HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() throws SQLException {
        var conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }
        conn.close();

        var repo = new LinkRepository(conn);
        var service = new LinkService(repo);
        var testOtel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setMeterProvider(SdkMeterProvider.builder().build())
                .build();
        var requestMetrics = new RequestMetrics(testOtel.getMeter("test"));
        var linkSpans = new LinkSpans(testOtel.getTracer("test"), testOtel.getMeter("test"));
        app = Javalin.create(config -> new LinkRoutes(service, requestMetrics, linkSpans).register(config.routes)).start(0);
        port = app.port();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    @Test
    void getIdReturns500WhenRepositoryThrowsUnexpectedly() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/anything")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertEquals("Internal error", response.body());
    }

    @Test
    void postLinkReturns500WhenRepositoryThrowsUnexpectedly() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://example.com\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertEquals("Internal error", response.body());
    }

    @Test
    void postLinkWithAliasReturns500WhenRepositoryThrowsUnexpectedly() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://example.com\",\"alias\":\"my-alias\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
        assertEquals("Internal error", response.body());
    }
}
