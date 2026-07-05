package linker.routes;

import io.javalin.Javalin;
import linker.config.FeatureFlags;

import java.io.InputStream;
import java.util.function.Function;

public final class StaticRoutes {
    private final FeatureFlags featureFlags;
    private final Function<String, InputStream> resourceLoader;

    public StaticRoutes(FeatureFlags featureFlags) {
        this(featureFlags, resource -> StaticRoutes.class.getResourceAsStream(resource));
    }

    public StaticRoutes(FeatureFlags featureFlags, Function<String, InputStream> resourceLoader) {
        this.featureFlags = featureFlags;
        this.resourceLoader = resourceLoader;
    }

    public void register(Javalin app) {
        app.get("/", ctx -> serve(ctx, "/v1/index.html", "text/html", "index.html not found"));
        app.get("/app.js", ctx -> serve(ctx, "/app.js", "application/javascript", "app.js not found"));
        app.get("/styles.css", ctx -> serve(ctx, "/v1/styles.css", "text/css", "styles.css not found"));
    }

    private void serve(io.javalin.http.Context ctx,
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
