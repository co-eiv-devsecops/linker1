package linker.routes;

import io.javalin.Javalin;
import linker.config.FeatureFlags;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
        app.get("/", ctx -> {
            String path = featureFlags.isNewUiEnabled() ? "/v2/index-v2.html" : "/v1/index.html";
            String notFound = featureFlags.isNewUiEnabled() ? "index-v2.html not found" : "index.html not found";
            serve(ctx, path, "text/html", notFound);
        });
        app.get("/app.js", ctx -> serve(ctx, "/app.js", "application/javascript", "app.js not found"));
        app.get("/styles.css", ctx -> {
            String path = featureFlags.isNewUiEnabled() ? "/v2/styles2.css" : "/v1/styles.css";
            String notFound = featureFlags.isNewUiEnabled() ? "styles2.css not found" : "styles.css not found";
            serve(ctx, path, "text/css", notFound);
        });
    }

    private void serve(io.javalin.http.Context ctx,
                       String resource, String contentType, String notFoundMessage) {
        try {
            var stream = resourceLoader.apply(resource);
            if (stream != null) {
                var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                if (contentType.equals("text/html")) {
                    ctx.html(content);
                } else {
                    // Without an explicit charset the server encodes the body as
                    // ISO-8859-1 and accented characters (e.g. "ó") reach the browser
                    // as invalid UTF-8 (shown as "?"). Send explicit UTF-8 bytes.
                    ctx.contentType(contentType + "; charset=utf-8")
                        .result(content.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                ctx.status(404).result(notFoundMessage);
            }
        } catch (Exception e) {
            ctx.status(500).result("Error reading " + resource.substring(1) + ": " + e.getMessage());
        }
    }
}
