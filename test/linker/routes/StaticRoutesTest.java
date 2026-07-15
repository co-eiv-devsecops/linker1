package linker.routes;

import io.javalin.Javalin;
import linker.config.FeatureFlags;
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
    static final FeatureFlags disabledFlags = new FeatureFlags() {
        @Override
        public boolean isNewUiEnabled() {
            return false;
        }
    };

    @BeforeAll
    static void startServer() {
<<<<<<< HEAD
        app = Javalin.create(config -> new StaticRoutes(disabledFlags).register(config.routes)).start(0);
=======
        app = Javalin.create().start(0);
        new StaticRoutes(disabledFlags).register(app);
>>>>>>> 45d714eede83fad01b0f5558c4b3250bcb57aca2
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
        var missingResourceApp = Javalin.create(config -> new StaticRoutes(disabledFlags, resource -> null).register(config.routes)).start(0);
        try {
<<<<<<< HEAD
=======
            new StaticRoutes(disabledFlags, resource -> null).register(missingResourceApp);
>>>>>>> 45d714eede83fad01b0f5558c4b3250bcb57aca2
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
        var missingResourceApp = Javalin.create(config -> new StaticRoutes(disabledFlags, resource -> null).register(config.routes)).start(0);
        try {
<<<<<<< HEAD
=======
            new StaticRoutes(disabledFlags, resource -> null).register(missingResourceApp);
>>>>>>> 45d714eede83fad01b0f5558c4b3250bcb57aca2
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
        var missingResourceApp = Javalin.create(config -> new StaticRoutes(disabledFlags, resource -> null).register(config.routes)).start(0);
        try {
<<<<<<< HEAD
=======
            new StaticRoutes(disabledFlags, resource -> null).register(missingResourceApp);
>>>>>>> 45d714eede83fad01b0f5558c4b3250bcb57aca2
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
        var failingResourceApp = Javalin.create(config -> new StaticRoutes(disabledFlags, resource -> {
            throw new RuntimeException("boom");
        }).register(config.routes)).start(0);
        try {
<<<<<<< HEAD
=======
            new StaticRoutes(disabledFlags, resource -> {
                throw new RuntimeException("boom");
            }).register(failingResourceApp);
>>>>>>> 45d714eede83fad01b0f5558c4b3250bcb57aca2
            var failingPort = failingResourceApp.port();

            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + failingPort + "/")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("boom"));
        } finally {
            failingResourceApp.stop();
        }
    }

    @Test
    void getRootWithNewUiEnabledReturnsV2Html() throws Exception {
        var flagEnabledFlags = new FeatureFlags() {
            @Override
            public boolean isNewUiEnabled() {
                return true;
            }
        };
<<<<<<< HEAD
        var appWithFlags = Javalin.create(config -> new StaticRoutes(flagEnabledFlags).register(config.routes)).start(0);
        try {
=======
        var appWithFlags = Javalin.create().start(0);
        try {
            new StaticRoutes(flagEnabledFlags).register(appWithFlags);
>>>>>>> 45d714eede83fad01b0f5558c4b3250bcb57aca2
            var testPort = appWithFlags.port();

            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + testPort + "/")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Colombia Edition"));
        } finally {
            appWithFlags.stop();
        }
    }

    @Test
    void getStylesCssWithNewUiEnabledReturnsV2Css() throws Exception {
        var flagEnabledFlags = new FeatureFlags() {
            @Override
            public boolean isNewUiEnabled() {
                return true;
            }
        };
<<<<<<< HEAD
        var appWithFlags = Javalin.create(config -> new StaticRoutes(flagEnabledFlags).register(config.routes)).start(0);
        try {
=======
        var appWithFlags = Javalin.create().start(0);
        try {
            new StaticRoutes(flagEnabledFlags).register(appWithFlags);
>>>>>>> 45d714eede83fad01b0f5558c4b3250bcb57aca2
            var testPort = appWithFlags.port();

            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + testPort + "/styles.css")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("css"));
            assertTrue(response.body().contains("--colombia-blue"));
        } finally {
            appWithFlags.stop();
        }
    }
}
