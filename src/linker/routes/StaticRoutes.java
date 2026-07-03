package linker.routes;

import io.javalin.Javalin;

public class StaticRoutes {

    public static void register(Javalin app) {
        app.get("/", ctx -> {
            try {
                var resource = StaticRoutes.class.getResourceAsStream("/index.html");
                if (resource != null) {
                    String html = new String(resource.readAllBytes());
                    ctx.html(html);
                } else {
                    ctx.status(404).result("index.html not found");
                }
            } catch (Exception e) {
                ctx.status(500).result("Error reading index.html: " + e.getMessage());
            }
        });

        app.get("/app.js", ctx -> {
            try {
                var resource = StaticRoutes.class.getResourceAsStream("/app.js");
                if (resource != null) {
                    ctx.contentType("application/javascript").result(new String(resource.readAllBytes()));
                } else {
                    ctx.status(404).result("app.js not found");
                }
            } catch (Exception e) {
                ctx.status(500).result("Error reading app.js");
            }
        });

        app.get("/styles.css", ctx -> {
            try {
                var resource = StaticRoutes.class.getResourceAsStream("/styles.css");
                if (resource != null) {
                    ctx.contentType("text/css").result(new String(resource.readAllBytes()));
                } else {
                    ctx.status(404).result("styles.css not found");
                }
            } catch (Exception e) {
                ctx.status(500).result("Error reading styles.css");
            }
        });
    }
}
