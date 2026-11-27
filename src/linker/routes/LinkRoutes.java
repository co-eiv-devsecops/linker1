package linker.routes;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import linker.AliasConflictException;
import linker.LinkService;
import linker.telemetry.LinkSpans;
import linker.telemetry.RequestMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class LinkRoutes {
    private static final Logger log = LoggerFactory.getLogger(LinkRoutes.class);

    private final LinkService service;
    private final RequestMetrics requestMetrics;
    private final LinkSpans linkSpans;

    public LinkRoutes(LinkService service, RequestMetrics requestMetrics, LinkSpans linkSpans) {
        this.service = service;
        this.requestMetrics = requestMetrics;
        this.linkSpans = linkSpans;
    }

    public void register(Javalin app) {
        app.get("/link/{id}/stats", ctx -> {
            log.trace("Received GET request on path={}", ctx.path());
            var id = ctx.pathParam("id");
            long start = System.currentTimeMillis();

            Long clicks;
            try {
                clicks = service.getClicks(id);
            } catch (Exception e) {
                log.error("Failed to read stats for link id={}", id, e);
                ctx.status(500).result("Internal error");
                requestMetrics.recordRequest("/link/{id}/stats", 500, System.currentTimeMillis() - start);
                return;
            }

            if (clicks == null) {
                ctx.status(404).result("Not found");
                requestMetrics.recordRequest("/link/{id}/stats", 404, System.currentTimeMillis() - start);
                return;
            }

            ctx.json(Map.of("id", id, "clicks", clicks));
            requestMetrics.recordRequest("/link/{id}/stats", 200, System.currentTimeMillis() - start);
        });

        app.get("/{id}", ctx -> {
            log.trace("Received GET request on path={}", ctx.path());
            var id = ctx.pathParam("id");
            long start = System.currentTimeMillis();
            log.debug("Resolving link id={}", id);

            String url;
            try {
                url = linkSpans.traceResolve(id, () -> service.open(id));
            } catch (Exception e) {
                log.error("Failed to resolve link id={}", id, e);
                ctx.status(500).result("Internal error");
                requestMetrics.recordRequest("/{id}", 500, System.currentTimeMillis() - start);
                return;
            }

            if (url != null) {
                // Destinations can contain private query parameters; do not put them in logs.
                log.info("Resolved link id={}", id);
                ctx.header("Cache-Control", "no-store");
                ctx.redirect(url, HttpStatus.FOUND);
                requestMetrics.recordRequest("/{id}", 302, System.currentTimeMillis() - start);
            } else {
                log.warn("Link id={} not found", id);
                ctx.status(404).result("Not found");
                requestMetrics.recordRequest("/{id}", 404, System.currentTimeMillis() - start);
            }
        });

        app.head("/{id}", ctx -> {
            log.trace("Received HEAD request on path={}", ctx.path());
            var id = ctx.pathParam("id");
            long start = System.currentTimeMillis();
            log.debug("Resolving link id={} for HEAD", id);

            String url;
            try {
                url = linkSpans.traceResolve(id, () -> service.get(id));
            } catch (Exception e) {
                log.error("Failed to resolve link id={} for HEAD", id, e);
                ctx.status(500).result("Internal error");
                requestMetrics.recordRequest("/{id}", 500, System.currentTimeMillis() - start);
                return;
            }

            if (url != null) {
                // HEAD still returns the destination by API contract, but avoid duplicating it in logs.
                log.info("Resolved link id={} for HEAD", id);
                ctx.status(200).result(url);
                requestMetrics.recordRequest("/{id}", 200, System.currentTimeMillis() - start);
            } else {
                log.warn("Link id={} for HEAD not found", id);
                ctx.status(404).result("Not found");
                requestMetrics.recordRequest("/{id}", 404, System.currentTimeMillis() - start);
            }
        });

        app.delete("/{id}", ctx -> {
            log.trace("Received DELETE request on path={}", ctx.path());
            var id = ctx.pathParam("id");
            long start = System.currentTimeMillis();
            log.debug("Deleting link id={}", id);

            boolean deleted;
            try {
                deleted = linkSpans.traceDelete(id, () -> service.delete(id));
            } catch (Exception e) {
                log.error("Failed to delete link id={}", id, e);
                ctx.status(500).result("Internal error");
                requestMetrics.recordRequest("/{id}", 500, System.currentTimeMillis() - start);
                return;
            }

            if (deleted) {
                log.info("Deleted link id={}", id);
                ctx.status(204);
                requestMetrics.recordRequest("/{id}", 204, System.currentTimeMillis() - start);
            } else {
                log.warn("Link id={} not found for deletion", id);
                ctx.status(404).result("Not found");
                requestMetrics.recordRequest("/{id}", 404, System.currentTimeMillis() - start);
            }
        });

        app.post("/link", ctx -> {
            log.trace("Received POST request on path={}", ctx.path());
            long start = System.currentTimeMillis();
            var url = ctx.formParam("url");
            var alias = ctx.formParam("alias");
            if (url == null && ctx.contentType() != null && ctx.contentType().contains("json")) {
                try {
                    var body = ctx.bodyAsClass(Map.class);
                    url = (String) body.get("url");
                    alias = (String) body.get("alias");
                } catch (Exception e) {
                    log.warn("Invalid JSON body on POST /link", e);
                    ctx.status(400).result("El cuerpo de la solicitud no contiene un JSON válido.");
                    requestMetrics.recordRequest("/link", 400, System.currentTimeMillis() - start);
                    return;
                }
            }

            log.debug("Creating link (aliasProvided={})", alias != null);
            final var finalUrl = url;
            final var finalAlias = alias;

            if (alias != null) {
                try {
                    var link = linkSpans.traceCreate(() -> service.create(finalUrl, finalAlias));
                    log.info("Created aliased link id={}", link.id());
                    ctx.status(HttpStatus.CREATED).header("Location", "/" + link.id()).result(link.id());
                    requestMetrics.recordRequest("/link", 201, System.currentTimeMillis() - start);
                } catch (AliasConflictException e) {
                    log.warn("Alias conflict during link creation");
                    ctx.status(409).result("El alias no está disponible. Elige otro.");
                    requestMetrics.recordRequest("/link", 409, System.currentTimeMillis() - start);
                } catch (IllegalArgumentException e) {
                    log.warn("Rejected invalid aliased link request");
                    ctx.status(400).result(invalidCreationMessage(finalUrl));
                    requestMetrics.recordRequest("/link", 400, System.currentTimeMillis() - start);
                } catch (Exception e) {
                    log.error("Failed to create aliased link", e);
                    ctx.status(500).result("Internal error");
                    requestMetrics.recordRequest("/link", 500, System.currentTimeMillis() - start);
                }
                return;
            }

            try {
                var result = linkSpans.traceCreate(() -> service.createResult(finalUrl));
                var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
                log.info("Created link id={} (new={})", result.link().id(), result.created());
                ctx.status(status).header("Location", "/" + result.link().id()).result(result.link().id());
                requestMetrics.recordRequest("/link", status.getCode(), System.currentTimeMillis() - start);
            } catch (IllegalArgumentException e) {
                log.warn("Rejected invalid destination URL on POST /link");
                ctx.status(400).result("La URL no es válida. Ingresa una dirección absoluta que comience con http:// o https:// y tenga un host.");
                requestMetrics.recordRequest("/link", 400, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("Failed to create link", e);
                ctx.status(500).result("Internal error");
                requestMetrics.recordRequest("/link", 500, System.currentTimeMillis() - start);
            }
        });
    }

    private static String invalidCreationMessage(String url) {
        if (!LinkService.isValidUrl(url)) {
            return "La URL no es válida. Ingresa una dirección absoluta que comience con http:// o https:// y tenga un host.";
        }
        return "El alias no es válido o está reservado. Usa entre 1 y 64 letras, números, guiones o guiones bajos.";
    }
}
