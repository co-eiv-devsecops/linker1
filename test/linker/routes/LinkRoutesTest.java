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
import com.fasterxml.jackson.databind.ObjectMapper;
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
        repo.ensureClicksColumn();
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

        assertEquals(302, response.statusCode());
        assertEquals("https://example.com", response.headers().firstValue("Location").orElse(null));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/does-not-exist")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void headExistingIdReturns200WithContentLength() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/existing")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("", response.body());
        assertEquals("19", response.headers().firstValue("Content-Length").orElse(null));
    }

    @Test
    void headUnknownIdReturns404() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/does-not-exist")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void deleteExistingIdReturns204AndSubsequentGetOrHeadReturns404() throws Exception {
        var createRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://delete-me.example.com\"}"))
                .build();
        var createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString());
        var id = createResponse.body();

        var deleteRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + id)).DELETE().build();
        var deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, deleteResponse.statusCode());

        var getRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + id)).GET().build();
        var getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, getResponse.statusCode());

        var headRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + id)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        var headResponse = client.send(headRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, headResponse.statusCode());
    }

    @Test
    void deleteUnknownIdReturns404() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/does-not-exist")).DELETE().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void headAndToDeletePropagateExceptionReturns500() throws Exception {
        var mockService = org.mockito.Mockito.mock(LinkService.class);
        org.mockito.Mockito.when(mockService.get(org.mockito.Mockito.anyString())).thenThrow(new SQLException("db error"));
        org.mockito.Mockito.when(mockService.delete(org.mockito.Mockito.anyString())).thenThrow(new SQLException("db error"));

        var testOtel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setMeterProvider(SdkMeterProvider.builder().build())
                .build();
        var requestMetrics = new RequestMetrics(testOtel.getMeter("test"));
        var linkSpans = new LinkSpans(testOtel.getTracer("test"), testOtel.getMeter("test"));

        var tempApp = Javalin.create().start(0);
        new LinkRoutes(mockService, requestMetrics, linkSpans).register(tempApp);
        int tempPort = tempApp.port();
        try {
            var requestHead = HttpRequest.newBuilder(URI.create("http://localhost:" + tempPort + "/existing")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            var responseHead = client.send(requestHead, HttpResponse.BodyHandlers.ofString());
            assertEquals(500, responseHead.statusCode());

            var requestDelete = HttpRequest.newBuilder(URI.create("http://localhost:" + tempPort + "/existing")).DELETE().build();
            var responseDelete = client.send(requestDelete, HttpResponse.BodyHandlers.ofString());
            assertEquals(500, responseDelete.statusCode());
        } finally {
            tempApp.stop();
        }
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
        assertTrue(response.body().contains("http://") && response.body().contains("https://"));
        assertFalse(response.body().contains("not a url"));
    }

    @Test
    void postLinkWithDangerousSchemeReturns400WithoutStoringUrl() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"javascript:alert(1)\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertFalse(response.body().contains("javascript"));
        assertTrue(response.body().contains("La URL no es válida"));
        try (var ps = conn.prepareStatement("SELECT COUNT(*) AS total FROM shorturl WHERE url = ?")) {
            ps.setString(1, "javascript:alert(1)");
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt("total"));
            }
        }
    }

    @Test
    void postLinkWithReservedHealthAliasReturnsHelpful400AndDoesNotPersist() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://health.example.com\",\"alias\":\"healthz\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("reservado"));
        try (var ps = conn.prepareStatement("SELECT url FROM shorturl WHERE id = 'healthz'");
             var rs = ps.executeQuery()) {
            assertFalse(rs.next());
        }
    }

    @Test
    void postAliasedLinkWithInvalidUrlReturnsUrlValidationMessage() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"javascript:alert(1)\",\"alias\":\"safe-name\"}"))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertTrue(response.body().startsWith("La URL no es válida"));
        assertFalse(response.body().contains("javascript"));
    }

    @Test
    void postLinkWithOccupiedAliasReturnsSpanishConflictWithoutEchoingAlias() throws Exception {
        var first = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://one.example.com\",\"alias\":\"no-disponible\"}"))
                .build();
        client.send(first, HttpResponse.BodyHandlers.ofString());
        var second = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"url\":\"https://two.example.com\",\"alias\":\"no-disponible\"}"))
                .build();
        var response = client.send(second, HttpResponse.BodyHandlers.ofString());

        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("El alias no está disponible"));
        assertFalse(response.body().contains("no-disponible"));
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
        assertEquals("El cuerpo de la solicitud no contiene un JSON válido.", response.body());
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

        assertEquals(302, response.statusCode());
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

    private static void createAlias(String alias, String url) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"url\":\"" + url + "\",\"alias\":\"" + alias + "\"}"))
                .build();
        assertEquals(201, client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private static HttpResponse<String> stats(String id) throws Exception {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/link/" + id + "/stats")).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static long clicksOf(String id) throws Exception {
        return new ObjectMapper().readTree(stats(id).body()).get("clicks").asLong();
    }

    private static void get(String id) throws Exception {
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/" + id)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    @Test
    void newLinkStartsWithZeroClicks() throws Exception {
        createAlias("stats-zero", "https://zero.example.com");
        assertEquals(0L, clicksOf("stats-zero"));
    }

    @Test
    void eachGetAddsOneClick() throws Exception {
        createAlias("stats-two", "https://two.example.com");
        get("stats-two");
        get("stats-two");
        assertEquals(2L, clicksOf("stats-two"));
    }

    @Test
    void headAndStatsDoNotAddClicks() throws Exception {
        createAlias("stats-head", "https://head.example.com");
        client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/stats-head"))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        stats("stats-head");
        assertEquals(0L, clicksOf("stats-head"));
    }

    @Test
    void statsReturns200WithIdAndClicksJson() throws Exception {
        createAlias("stats-json", "https://json.example.com");
        var response = stats("stats-json");
        assertEquals(200, response.statusCode());
        var json = new ObjectMapper().readTree(response.body());
        assertEquals("stats-json", json.get("id").asText());
        assertEquals(0, json.get("clicks").asInt());
    }

    @Test
    void statsForUnknownIdReturns404() throws Exception {
        assertEquals(404, stats("no-such-link").statusCode());
    }

    @Test
    void getUnknownIdDoesNotCreateOrCountAnything() throws Exception {
        get("ghost-link");
        assertEquals(404, stats("ghost-link").statusCode());
    }
}
