package linker.telemetry;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMetricsTest {

    InMemoryMetricReader reader;
    SystemMetrics systemMetrics;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        var meterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build();
        var meter = meterProvider.get("test");
        systemMetrics = new SystemMetrics(meter);
    }

    @Test
    void setLinkCountRecordsTheGivenValue() {
        systemMetrics.setLinkCount(42);

        var metric = findMetric("linker.links.count");
        var point = metric.getLongGaugeData().getPoints().iterator().next();
        assertEquals(42, point.getValue());
    }

    @Test
    void recordHeapUsageRecordsANonNegativeValue() {
        systemMetrics.recordHeapUsage();

        var metric = findMetric("linker.jvm.heap.used");
        var point = metric.getLongGaugeData().getPoints().iterator().next();
        assertTrue(point.getValue() >= 0);
    }

    @Test
    void recordHeapUsageRecordsAPositiveMaxValue() {
        systemMetrics.recordHeapUsage();

        var metric = findMetric("linker.jvm.heap.max");
        var point = metric.getLongGaugeData().getPoints().iterator().next();
        assertTrue(point.getValue() > 0);
    }

    @Test
    void recordThreadCountRecordsAPositiveValue() {
        systemMetrics.recordThreadCount();

        var metric = findMetric("linker.jvm.threads");
        var point = metric.getLongGaugeData().getPoints().iterator().next();
        assertTrue(point.getValue() > 0);
    }

    @Test
    void recordUptimeRecordsANonNegativeValue() {
        systemMetrics.recordUptime();

        var metric = findMetric("linker.process.uptime");
        var point = metric.getLongGaugeData().getPoints().iterator().next();
        assertTrue(point.getValue() >= 0);
    }

    private io.opentelemetry.sdk.metrics.data.MetricData findMetric(String name) {
        return reader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Metric not found: " + name));
    }
}
