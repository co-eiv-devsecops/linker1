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
        var testOtel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setMeterProvider(SdkMeterProvider.builder().build())
                .build();
        var requestMetrics = new RequestMetrics(testOtel.getMeter("test"));
        var linkSpans = new LinkSpans(testOtel.getTracer("test"), testOtel.getMeter("test"));
        app = Javalin.create().start(0);
        new LinkRoutes(service, requestMetrics, linkSpans).register(app);
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

    @Test
    void postLinkWithValidUrlReturns201WithLocationHeader() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://newlink.example.com\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Location").isPresent());
    }

    @Test
    void postLinkWithSameUrlTwiceReturnsSameId() throws Exception {
        var body = "{\"url\":\"https://repeat.example.com\"}";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        var first = client.send(request, HttpResponse.BodyHandlers.ofString());
        var second = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(first.body(), second.body());
    }

    @Test
    void postLinkWithInvalidUrlReturns400() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"not a url\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    @Test
    void postLinkWithNoUrlAndNonJsonContentTypeReturns400() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("irrelevant"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    @Test
    void postLinkWithMalformedJsonReturns400() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not valid json"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertEquals("Invalid JSON", response.body());
    }

    @Test
    void postLinkWithAvailableAliasReturns201WithAliasAsLocation() throws Exception {
        var body = "{\"url\":\"https://aliased.example.com\",\"alias\":\"my-cool-alias\"}";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertEquals("/my-cool-alias", response.headers().firstValue("Location").orElse(null));
    }

    @Test
    void postLinkWithTakenAliasReturns409() throws Exception {
        var body = "{\"url\":\"https://first.example.com\",\"alias\":\"taken-alias\"}";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());

        var conflictBody = "{\"url\":\"https://second.example.com\",\"alias\":\"taken-alias\"}";
        var conflictRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(conflictBody))
                .build();
        var response = client.send(conflictRequest, HttpResponse.BodyHandlers.ofString());

        assertEquals(409, response.statusCode());
    }

    @Test
    void postLinkWithInvalidAliasReturns400() throws Exception {
        var body = "{\"url\":\"https://example.com\",\"alias\":\"has space\"}";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
    }

    @Test
    void getAliasedIdRedirectsToStoredUrl() throws Exception {
        var body = "{\"url\":\"https://redirect-check.example.com\",\"alias\":\"redirect-alias\"}";
        var createRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.send(createRequest, HttpResponse.BodyHandlers.ofString());

        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/redirect-alias")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(301, response.statusCode());
        assertEquals("https://redirect-check.example.com", response.headers().firstValue("Location").orElse(null));
    }

    @Test
    void postLinkFormParametersReturns201() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("url=https%3A%2F%2Fform.example.com"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Location").isPresent());
    }
}
