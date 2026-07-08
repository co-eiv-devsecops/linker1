package linker.health;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthCheckMetricsTest {

    InMemoryMetricReader reader;
    HealthCheckMetrics metrics;

    @BeforeEach
    void setUp() {
        reader = InMemoryMetricReader.create();
        var meterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build();
        metrics = new HealthCheckMetrics(meterProvider.get("test"));
    }

    @Test
    void recordCheckIncrementsChecksCounterOnSuccess() {
        metrics.recordCheck(true, 12);

        var metric = findMetric("linker.healthcheck.checks");
        var point = metric.getLongSumData().getPoints().iterator().next();
        assertEquals(1, point.getValue());
    }

    @Test
    void recordCheckDoesNotIncrementFailuresOnSuccess() {
        metrics.recordCheck(true, 12);

        var checks = reader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals("linker.healthcheck.failures"))
                .findFirst();
        assertEquals(false, checks.isPresent());
    }

    @Test
    void recordCheckSetsUpGaugeToOneOnSuccess() {
        metrics.recordCheck(true, 12);

        var metric = findMetric("linker.healthcheck.up");
        var point = metric.getLongGaugeData().getPoints().iterator().next();
        assertEquals(1, point.getValue());
    }

    @Test
    void recordCheckIncrementsFailuresCounterOnFailure() {
        metrics.recordCheck(false, 5);

        var metric = findMetric("linker.healthcheck.failures");
        var point = metric.getLongSumData().getPoints().iterator().next();
        assertEquals(1, point.getValue());
    }

    @Test
    void recordCheckSetsUpGaugeToZeroOnFailure() {
        metrics.recordCheck(false, 5);

        var metric = findMetric("linker.healthcheck.up");
        var point = metric.getLongGaugeData().getPoints().iterator().next();
        assertEquals(0, point.getValue());
    }

    @Test
    void recordCheckRecordsDuration() {
        metrics.recordCheck(true, 42);

        var metric = findMetric("linker.healthcheck.duration");
        var point = metric.getHistogramData().getPoints().iterator().next();
        assertEquals(42, point.getSum());
    }

    private MetricData findMetric(String name) {
        return reader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Metric not found: " + name));
    }
}
