package linker.health;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;

/**
 * Verifies connectivity to the MySQL backing service by running
 * {@code SELECT 1} against a fresh connection on every check -- this is a
 * liveness/backing-service probe, not a cached status, so it reflects the
 * database's real state at the moment {@code /healthz} is called.
 *
 * <p>The MySQL call is wrapped in a span of kind {@link SpanKind#SERVER},
 * per the course's explicit instrumentation requirement for this check.
 *
 * <p>Takes a {@code Supplier<Connection>} rather than a single long-lived
 * {@link Connection}: each check opens (and closes) its own connection, so a
 * database outage only ever fails an individual health check request instead
 * of crashing the whole application at startup or leaking a stale connection.
 */
public class HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(HealthCheck.class);

    public record Result(boolean healthy, String detail) {
    }

    private final Tracer tracer;
    private final Supplier<Connection> connectionSupplier;

    public HealthCheck(Tracer tracer, Supplier<Connection> connectionSupplier) {
        this.tracer = tracer;
        this.connectionSupplier = connectionSupplier;
    }

    public Result check() {
        Span span = tracer.spanBuilder("mysql.healthcheck")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("db.system", "mysql")
                .setAttribute("db.statement", "SELECT 1")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            return runCheck(span);
        } finally {
            span.end();
        }
    }

    private Result runCheck(Span span) {
        try (Connection conn = connectionSupplier.get();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            span.setStatus(StatusCode.OK);
            log.debug("MySQL healthcheck passed");
            return new Result(true, "OK");
        } catch (SQLException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            log.error("MySQL healthcheck failed", e);
            return new Result(false, e.getMessage());
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            log.error("MySQL healthcheck failed to obtain a connection", e);
            return new Result(false, e.getMessage());
        }
    }
}
