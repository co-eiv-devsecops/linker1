error id: file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/linker/routes/StaticRoutes.java:io/javalin/http/Context#html#
file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/linker/routes/StaticRoutes.java
empty definition using pc, found symbol in pc: io/javalin/http/Context#html#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 1255
uri: file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/linker/routes/StaticRoutes.java
text:
```scala
package linker.routes;

import io.javalin.Javalin;

import java.io.InputStream;
import java.util.function.Function;

public final class StaticRoutes {

    private StaticRoutes() {
    }

    public static void register(Javalin app) {
        register(app, resource -> StaticRoutes.class.getResourceAsStream(resource));
    }

    public static void register(Javalin app, Function<String, InputStream> resourceLoader) {
        app.get("/", ctx -> serve(ctx, resourceLoader, "/index-v2.html", "text/html", "index.html not found"));
        app.get("/app.js", ctx -> serve(ctx, resourceLoader, "/app.js", "application/javascript", "app.js not found"));
        app.get("/styles.css", ctx -> serve(ctx, resourceLoader, "/styles2.css", "text/css", "styles.css not found"));
    }

    private static void serve(io.javalin.http.Context ctx, Function<String, InputStream> resourceLoader,
                               String resource, String contentType, String notFoundMessage) {
        try {
            var stream = resourceLoader.apply(resource);
            if (stream != null) {
                var content = new String(stream.readAllBytes());
                if (contentType.equals("text/html")) {
                    ctx.@@html(content);
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

```


#### Short summary: 

empty definition using pc, found symbol in pc: io/javalin/http/Context#html#