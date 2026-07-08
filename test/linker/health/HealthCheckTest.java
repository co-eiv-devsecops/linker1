package linker.health;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckTest {

    InMemorySpanExporter spanExporter;
    io.opentelemetry.api.trace.Tracer tracer;
    HealthCheckMetrics metrics;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        tracer = tracerProvider.get("test");
        metrics = new HealthCheckMetrics(SdkMeterProvider.builder().build().get("test"));
    }

    @Test
    void checkReturnsHealthyWhenConnectionAndQuerySucceed() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        var healthCheck = new HealthCheck(tracer, () -> conn, metrics);

        var result = healthCheck.check();

        assertTrue(result.healthy());
        assertEquals("OK", result.detail());
        conn.close();
    }

    @Test
    void checkProducesAServerKindSpanNamedMysqlHealthcheck() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        var healthCheck = new HealthCheck(tracer, () -> conn, metrics);

        healthCheck.check();

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        var span = spans.get(0);
        assertEquals("mysql.healthcheck", span.getName());
        assertEquals(SpanKind.SERVER, span.getKind());
        conn.close();
    }

    @Test
    void checkMarksSpanOkWhenHealthy() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        var healthCheck = new HealthCheck(tracer, () -> conn, metrics);

        healthCheck.check();

        var span = spanExporter.getFinishedSpanItems().get(0);
        assertEquals(StatusCode.OK, span.getStatus().getStatusCode());
        conn.close();
    }

    @Test
    void checkReturnsUnhealthyWhenConnectionSupplierThrows() {
        var healthCheck = new HealthCheck(tracer, () -> {
            throw new RuntimeException("connection refused");
        }, metrics);

        var result = healthCheck.check();

        assertFalse(result.healthy());
        assertEquals("connection refused", result.detail());
    }

    @Test
    void checkMarksSpanErrorWhenConnectionSupplierThrows() {
        var healthCheck = new HealthCheck(tracer, () -> {
            throw new RuntimeException("connection refused");
        }, metrics);

        healthCheck.check();

        var span = spanExporter.getFinishedSpanItems().get(0);
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals(SpanKind.SERVER, span.getKind());
    }

    @Test
    void checkReturnsUnhealthyWhenQueryFails() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.close();
        var healthCheck = new HealthCheck(tracer, () -> conn, metrics);

        var result = healthCheck.check();

        assertFalse(result.healthy());
    }
}
