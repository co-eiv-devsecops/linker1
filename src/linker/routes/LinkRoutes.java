package linker.routes;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import linker.LinkService;

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
    }
}
