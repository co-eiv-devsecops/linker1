package linker.routes;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class StaticRoutesTest {

    static Javalin app;
    static int port;
    static HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        app = Javalin.create().start(0);
        StaticRoutes.register(app);
        port = app.port();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    @Test
    void getRootReturnsIndexHtml() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<html"));
    }

    @Test
    void getAppJsReturnsJavascript() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/app.js")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("javascript"));
    }

    @Test
    void getStylesCssReturnsCss() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/styles.css")).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("css"));
    }

    @Test
    void getRootReturns404WhenResourceMissing() throws Exception {
        var missingResourceApp = Javalin.create().start(0);
        try {
            StaticRoutes.register(missingResourceApp, resource -> null);
            var missingPort = missingResourceApp.port();

            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + missingPort + "/")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode());
            assertEquals("index.html not found", response.body());
        } finally {
            missingResourceApp.stop();
        }
    }

    @Test
    void getAppJsReturns404WhenResourceMissing() throws Exception {
        var missingResourceApp = Javalin.create().start(0);
        try {
            StaticRoutes.register(missingResourceApp, resource -> null);
            var missingPort = missingResourceApp.port();

            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + missingPort + "/app.js")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode());
            assertEquals("app.js not found", response.body());
        } finally {
            missingResourceApp.stop();
        }
    }

    @Test
    void getStylesCssReturns404WhenResourceMissing() throws Exception {
        var missingResourceApp = Javalin.create().start(0);
        try {
            StaticRoutes.register(missingResourceApp, resource -> null);
            var missingPort = missingResourceApp.port();

            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + missingPort + "/styles.css")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode());
            assertEquals("styles.css not found", response.body());
        } finally {
            missingResourceApp.stop();
        }
    }

    @Test
    void getRootReturns500WhenResourceLoaderThrows() throws Exception {
        var failingResourceApp = Javalin.create().start(0);
        try {
            StaticRoutes.register(failingResourceApp, resource -> {
                throw new RuntimeException("boom");
            });
            var failingPort = failingResourceApp.port();

            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + failingPort + "/")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("boom"));
        } finally {
            failingResourceApp.stop();
        }
    }
}
