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
        app.get("/{id}", ctx -> {
            log.trace("Received GET request on path={}", ctx.path());
            var id = ctx.pathParam("id");
            long start = System.currentTimeMillis();
            log.debug("Resolving link id={}", id);

            String url;
            try {
                url = linkSpans.traceResolve(id, () -> service.get(id));
            } catch (Exception e) {
                log.error("Failed to resolve link id={}", id, e);
                ctx.status(500).result("Internal error");
                requestMetrics.recordRequest("/{id}", 500, System.currentTimeMillis() - start);
                return;
            }

            if (url != null) {
                log.info("Resolved link id={} to url={}", id, url);
                ctx.redirect(url, HttpStatus.MOVED_PERMANENTLY);
                requestMetrics.recordRequest("/{id}", 301, System.currentTimeMillis() - start);
            } else {
                log.warn("Link id={} not found", id);
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
                    ctx.status(400).result("Invalid JSON");
                    requestMetrics.recordRequest("/link", 400, System.currentTimeMillis() - start);
                    return;
                }
            }

            log.debug("Creating link url={} alias={}", url, alias);
            final var finalUrl = url;
            final var finalAlias = alias;

            if (alias != null) {
                try {
                    var link = linkSpans.traceCreate(url, () -> service.create(finalUrl, finalAlias));
                    log.info("Created aliased link id={} url={}", link.id(), url);
                    ctx.status(HttpStatus.CREATED).header("Location", "/" + link.id()).result(link.id());
                    requestMetrics.recordRequest("/link", 201, System.currentTimeMillis() - start);
                } catch (AliasConflictException e) {
                    log.warn("Alias conflict for alias={}", alias);
                    ctx.status(409).result(e.getMessage());
                    requestMetrics.recordRequest("/link", 409, System.currentTimeMillis() - start);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid request creating aliased link: {}", e.getMessage());
                    ctx.status(400).result(e.getMessage());
                    requestMetrics.recordRequest("/link", 400, System.currentTimeMillis() - start);
                } catch (Exception e) {
                    log.error("Failed to create aliased link url={} alias={}", url, alias, e);
                    ctx.status(500).result("Internal error");
                    requestMetrics.recordRequest("/link", 500, System.currentTimeMillis() - start);
                }
                return;
            }

            try {
                var result = linkSpans.traceCreate(url, () -> service.createResult(finalUrl));
                var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
                log.info("Created link id={} url={} (new={})", result.link().id(), url, result.created());
                ctx.status(status).header("Location", "/" + result.link().id()).result(result.link().id());
                requestMetrics.recordRequest("/link", status.getCode(), System.currentTimeMillis() - start);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid URL on POST /link: {}", url);
                ctx.status(400).result("Invalid URL");
                requestMetrics.recordRequest("/link", 400, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("Failed to create link url={}", url, e);
                ctx.status(500).result("Internal error");
                requestMetrics.recordRequest("/link", 500, System.currentTimeMillis() - start);
            }
        });
    }
}