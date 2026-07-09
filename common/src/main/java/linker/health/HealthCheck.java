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

public class HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(HealthCheck.class);

    public record Result(boolean healthy, String detail) {
    }

    private final Tracer tracer;
    private final Supplier<Connection> connectionSupplier;
    private final HealthCheckMetrics metrics;

    public HealthCheck(Tracer tracer, Supplier<Connection> connectionSupplier, HealthCheckMetrics metrics) {
        this.tracer = tracer;
        this.connectionSupplier = connectionSupplier;
        this.metrics = metrics;
    }

    public Result check() {
        Span span = tracer.spanBuilder("mysql.healthcheck")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("db.system", "mysql")
                .setAttribute("db.statement", "SELECT 1")
                .startSpan();

        long start = System.currentTimeMillis();
        try (Scope scope = span.makeCurrent()) {
            Result result = runCheck(span);
            metrics.recordCheck(result.healthy(), System.currentTimeMillis() - start);
            return result;
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