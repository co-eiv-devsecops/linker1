package linker.routes;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import linker.LinkService;

import java.util.Map;

public class LinkRoutes {

    public static void register(Javalin app, LinkService service) {
        app.get("/{id}", ctx -> {
            var id = ctx.pathParam("id");
            var url = service.get(id);
            if (url != null) {
                ctx.redirect(url, HttpStatus.MOVED_PERMANENTLY);
            } else {
                ctx.status(404).result("Not found");
            }
        });

        app.post("/link", ctx -> {
            var url = ctx.formParam("url");
            if (url == null && ctx.contentType() != null && ctx.contentType().contains("json")) {
                try {
                    var body = ctx.bodyAsClass(Map.class);
                    url = (String) body.get("url");
                } catch (Exception e) {
                    ctx.status(400).result("Invalid JSON");
                    return;
                }
            }

            try {
                var result = service.createResult(url);
                var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
                ctx.status(status).header("Location", "/" + result.link().id()).result(result.link().id());
            } catch (IllegalArgumentException e) {
                ctx.status(400).result("Invalid URL");
            }
        });
    }
}
