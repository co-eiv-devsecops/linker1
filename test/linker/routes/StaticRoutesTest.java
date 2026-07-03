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
}
