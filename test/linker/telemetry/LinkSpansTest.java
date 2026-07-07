package linker.telemetry;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkSpansTest {

    InMemorySpanExporter spanExporter;
    InMemoryMetricReader metricReader;
    LinkSpans linkSpans;

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        metricReader = InMemoryMetricReader.create();
        var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        var meterProvider = SdkMeterProvider.builder().registerMetricReader(metricReader).build();
        linkSpans = new LinkSpans(tracerProvider.get("test"), meterProvider.get("test"));
    }

    @Test
    void traceCreateProducesParentAndChildSpans() {
        var result = linkSpans.traceCreate("https://example.com", () -> "abc123");

        assertEquals("abc123", result);
        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(2, spans.size());
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("link.create")));
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("link.create.persist")));
    }

    @Test
    void traceResolveProducesParentAndChildSpans() {
        var result = linkSpans.traceResolve("abc123", () -> "https://example.com");

        assertEquals("https://example.com", result);
        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(2, spans.size());
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("link.resolve")));
        assertTrue(spans.stream().anyMatch(s -> s.getName().equals("link.resolve.lookup")));
    }

    @Test
    void traceCreateRecordsDbOperationDurationHistogram() {
        linkSpans.traceCreate("https://example.com", () -> "abc123");

        var metric = metricReader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals("linker.db.operation.duration"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Histogram not found"));
        var points = metric.getHistogramData().getPoints();
        assertEquals(1, points.size());
        assertEquals("create", points.iterator().next().getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("operation")));
    }

    @Test
    void traceResolvePropagatesCheckedExceptionAndMarksSpanAsError() {
        assertThrows(SQLException.class, () -> linkSpans.traceResolve("abc123", () -> {
            throw new SQLException("boom");
        }));

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(2, spans.size());
        spans.forEach(s -> assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, s.getStatus().getStatusCode()));
    }
}
