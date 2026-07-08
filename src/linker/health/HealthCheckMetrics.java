package linker.health;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;

/**
 * RED-method metrics for the MySQL health check ({@code GET /healthz}), following
 * the same pattern as {@link linker.telemetry.RequestMetrics}: callers never touch
 * a {@link Meter} instrument directly, they call {@link #recordCheck(boolean, long)}
 * once per check.
 */
public class HealthCheckMetrics {

    private final LongCounter checksCounter;
    private final LongCounter failuresCounter;
    private final LongHistogram checkDuration;
    private final LongGauge upGauge;

    public HealthCheckMetrics(Meter meter) {
        this.checksCounter = meter.counterBuilder("linker.healthcheck.checks")
                .setDescription("Total number of MySQL health checks performed")
                .setUnit("{check}")
                .build();

        this.failuresCounter = meter.counterBuilder("linker.healthcheck.failures")
                .setDescription("Total number of MySQL health checks that failed")
                .setUnit("{check}")
                .build();

        this.checkDuration = meter.histogramBuilder("linker.healthcheck.duration")
                .setDescription("Duration of the MySQL health check (connect + SELECT 1)")
                .setUnit("ms")
                .ofLongs()
                .build();

        this.upGauge = meter.gaugeBuilder("linker.healthcheck.up")
                .setDescription("Whether the last MySQL health check was healthy (1) or not (0)")
                .setUnit("{status}")
                .ofLongs()
                .build();
    }

    public void recordCheck(boolean healthy, long durationMillis) {
        Attributes attributes = Attributes.builder()
                .put("outcome", healthy ? "healthy" : "unhealthy")
                .build();

        checksCounter.add(1, attributes);
        checkDuration.record(durationMillis, attributes);
        upGauge.set(healthy ? 1 : 0);
        if (!healthy) {
            failuresCounter.add(1, attributes);
        }
    }
}
