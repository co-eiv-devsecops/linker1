package linker.health;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HealthRoutes {
    private static final Logger log = LoggerFactory.getLogger(HealthRoutes.class);

    private final HealthCheck healthCheck;

    public HealthRoutes(HealthCheck healthCheck) {
        this.healthCheck = healthCheck;
    }

    public void register(Javalin app) {
        app.get("/healthz", ctx -> {
            log.trace("Received GET request on path={}", ctx.path());
            var result = healthCheck.check();
            if (result.healthy()) {
                log.debug("Healthcheck OK");
                ctx.status(200).result("OK");
            } else {
                log.warn("Healthcheck failed: {}", result.detail());
                ctx.status(503).result("Unhealthy: " + result.detail());
            }
        });
    }
}