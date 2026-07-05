package linker.routes;

import io.javalin.Javalin;
import linker.config.FeatureFlags;

import java.io.InputStream;
import java.util.function.Function;

public final class StaticRoutes {

    private StaticRoutes() {
    }

    public static void register(Javalin app,  FeatureFlags featureFlags) {
        register(app, resource -> StaticRoutes.class.getResourceAsStream(resource));
    }

    public static void register(Javalin app, Function<String, InputStream> resourceLoader) {
        app.get("/", ctx -> serve(ctx, resourceLoader, "/v1/index.html", "text/html", "index.html not found"));
        app.get("/app.js", ctx -> serve(ctx, resourceLoader, "/app.js", "application/javascript", "app.js not found"));
        app.get("/styles.css", ctx -> serve(ctx, resourceLoader, "/v1/styles.css", "text/css", "styles.css not found"));
    }

    private static void serve(io.javalin.http.Context ctx, Function<String, InputStream> resourceLoader,
                               String resource, String contentType, String notFoundMessage) {
        try {
            var stream = resourceLoader.apply(resource);
            if (stream != null) {
                var content = new String(stream.readAllBytes());
                if (contentType.equals("text/html")) {
                    ctx.html(content);
                } else {
                    ctx.contentType(contentType).result(content);
                }
            } else {
                ctx.status(404).result(notFoundMessage);
            }
        } catch (Exception e) {
            ctx.status(500).result("Error reading " + resource.substring(1) + ": " + e.getMessage());
        }
    }
}
