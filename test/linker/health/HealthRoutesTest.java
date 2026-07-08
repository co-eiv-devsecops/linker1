package linker.health;

import io.javalin.Javalin;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthRoutesTest {

    static HttpClient client = HttpClient.newHttpClient();

    @Test
    void healthzReturns200WhenDatabaseReachable() throws Exception {
        var tracer = SdkTracerProvider.builder().build().get("test");
        var conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        var healthCheck = new HealthCheck(tracer, () -> conn);
        var app = Javalin.create().start(0);
        try {
            new HealthRoutes(healthCheck).register(app);
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/healthz")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("OK", response.body());
        } finally {
            app.stop();
            conn.close();
        }
    }

    @Test
    void healthzReturns503WhenDatabaseUnreachable() throws Exception {
        var tracer = SdkTracerProvider.builder().build().get("test");
        var healthCheck = new HealthCheck(tracer, () -> {
            throw new RuntimeException("connection refused");
        });
        var app = Javalin.create().start(0);
        try {
            new HealthRoutes(healthCheck).register(app);
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/healthz")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(503, response.statusCode());
            assertTrue(response.body().contains("connection refused"));
        } finally {
            app.stop();
        }
    }
}
