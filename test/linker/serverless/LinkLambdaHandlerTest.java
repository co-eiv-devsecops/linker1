package linker.serverless;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import linker.LinkRepository;
import linker.LinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinkLambdaHandlerTest {

    Connection conn;
    LinkLambdaHandler handler;

    @BeforeEach
    void setUp() throws SQLException {
        // LinkRepository takes any java.sql.Connection -- SQLite in-memory
        // here exercises the exact same handler/service code path that runs
        // against MySQL in the real Lambda deployment, without needing a
        // live database for unit tests.
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }
        handler = new LinkLambdaHandler(new LinkService(new LinkRepository(conn)));
    }

    private APIGatewayProxyRequestEvent request(String method, String path, String body) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withPath(path)
                .withBody(body);
    }

    private APIGatewayProxyRequestEvent requestWithPathParam(String method, String path, String id, String body) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withPath(path)
                .withPathParameters(Map.of("id", id))
                .withBody(body);
    }

    // ── POST /link ────────────────────────────────────────────────────────

    @Test
    void createWithValidUrlReturns201() {
        var req = request("POST", "/link", "{\"url\":\"https://example.com\"}");
        APIGatewayProxyResponseEvent resp = handler.handleRequest(req, null);

        assertEquals(201, resp.getStatusCode());
        assertNotNull(resp.getHeaders().get("Location"));
        assertEquals(8, resp.getBody().length());
    }

    @Test
    void createWithSameUrlTwiceDedupesAndReturns200() {
        var req = request("POST", "/link", "{\"url\":\"https://dedupe.example.com\"}");
        var first = handler.handleRequest(req, null);
        var second = handler.handleRequest(req, null);

        assertEquals(201, first.getStatusCode());
        assertEquals(200, second.getStatusCode());
        assertEquals(first.getBody(), second.getBody());
    }

    @Test
    void createWithInvalidUrlReturns400() {
        var req = request("POST", "/link", "{\"url\":\"not a url\"}");
        var resp = handler.handleRequest(req, null);
        assertEquals(400, resp.getStatusCode());
    }

    @Test
    void createWithInvalidJsonReturns400() {
        var req = request("POST", "/link", "{not json");
        var resp = handler.handleRequest(req, null);
        assertEquals(400, resp.getStatusCode());
    }

    @Test
    void createWithNullBodyDefaultsToEmptyObjectAndReturns400() {
        var req = request("POST", "/link", null);
        var resp = handler.handleRequest(req, null);
        assertEquals(400, resp.getStatusCode());
    }

    @Test
    void createWithFreshAliasReturns201WithAliasLocation() {
        var req = request("POST", "/link", "{\"url\":\"https://aliased.example.com\",\"alias\":\"my-alias\"}");
        var resp = handler.handleRequest(req, null);

        assertEquals(201, resp.getStatusCode());
        assertEquals("/my-alias", resp.getHeaders().get("Location"));
        assertEquals("my-alias", resp.getBody());
    }

    @Test
    void createWithTakenAliasReturns409() {
        handler.handleRequest(request("POST", "/link", "{\"url\":\"https://one.example.com\",\"alias\":\"taken\"}"), null);
        var resp = handler.handleRequest(request("POST", "/link", "{\"url\":\"https://two.example.com\",\"alias\":\"taken\"}"), null);
        assertEquals(409, resp.getStatusCode());
    }

    @Test
    void createWithInvalidAliasReturns400() {
        var req = request("POST", "/link", "{\"url\":\"https://example.com\",\"alias\":\"has space\"}");
        var resp = handler.handleRequest(req, null);
        assertEquals(400, resp.getStatusCode());
    }

    // ── GET /{id} ─────────────────────────────────────────────────────────

    @Test
    void getExistingIdRedirectsWith301() {
        var created = handler.handleRequest(request("POST", "/link", "{\"url\":\"https://get-test.example.com\"}"), null);
        var id = created.getBody();

        var resp = handler.handleRequest(requestWithPathParam("GET", "/" + id, id, null), null);
        assertEquals(301, resp.getStatusCode());
        assertEquals("https://get-test.example.com", resp.getHeaders().get("Location"));
    }

    @Test
    void getUnknownIdReturns404() {
        var resp = handler.handleRequest(requestWithPathParam("GET", "/missing", "missing", null), null);
        assertEquals(404, resp.getStatusCode());
    }

    @Test
    void getWithoutPathParametersFallsBackToPathParsing() {
        handler.handleRequest(request("POST", "/link", "{\"url\":\"https://path-fallback.example.com\",\"alias\":\"path-fallback\"}"), null);
        var resp = handler.handleRequest(request("GET", "/path-fallback", null), null);
        assertEquals(301, resp.getStatusCode());
    }

    @Test
    void getRootPathReturns404NotFound() {
        var resp = handler.handleRequest(request("GET", "/", null), null);
        assertEquals(404, resp.getStatusCode());
    }

    // ── HEAD /{id} ────────────────────────────────────────────────────────

    @Test
    void headExistingIdReturns200WithUrlBody() {
        var created = handler.handleRequest(request("POST", "/link", "{\"url\":\"https://head-test.example.com\"}"), null);
        var id = created.getBody();

        var resp = handler.handleRequest(requestWithPathParam("HEAD", "/" + id, id, null), null);
        assertEquals(200, resp.getStatusCode());
        assertEquals("https://head-test.example.com", resp.getBody());
    }

    @Test
    void headUnknownIdReturns404() {
        var resp = handler.handleRequest(requestWithPathParam("HEAD", "/missing", "missing", null), null);
        assertEquals(404, resp.getStatusCode());
    }

    // ── DELETE /{id} ──────────────────────────────────────────────────────

    @Test
    void deleteExistingIdReturns204() {
        var created = handler.handleRequest(request("POST", "/link", "{\"url\":\"https://delete-test.example.com\"}"), null);
        var id = created.getBody();

        var resp = handler.handleRequest(requestWithPathParam("DELETE", "/" + id, id, null), null);
        assertEquals(204, resp.getStatusCode());

        var afterDelete = handler.handleRequest(requestWithPathParam("GET", "/" + id, id, null), null);
        assertEquals(404, afterDelete.getStatusCode());
    }

    @Test
    void deleteUnknownIdReturns404() {
        var resp = handler.handleRequest(requestWithPathParam("DELETE", "/missing", "missing", null), null);
        assertEquals(404, resp.getStatusCode());
    }

    // ── Unsupported methods / error handling ─────────────────────────────

    @Test
    void unsupportedMethodOnIdPathReturns405() {
        var resp = handler.handleRequest(requestWithPathParam("PUT", "/some-id", "some-id", null), null);
        assertEquals(405, resp.getStatusCode());
    }

    @Test
    void exceptionDuringHandlingReturns500() throws SQLException {
        conn.close();
        var resp = handler.handleRequest(requestWithPathParam("GET", "/anything", "anything", null), null);
        assertEquals(500, resp.getStatusCode());
    }
}
