package linker.telemetry;

import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;

/**
 * Point-in-time gauges describing the current state of the system, following
 * the USE method (Utilization) from the RED/USE instrumentation model: how many
 * links currently exist, and how much of the JVM heap is in use right now.
 *
 * <p>Callers update these whenever the underlying value changes ({@link #setLinkCount(long)})
 * or on a schedule ({@link #recordHeapUsage()}) -- they never touch the OpenTelemetry
 * {@link Meter} API directly.
 */
public class SystemMetrics {

    private final LongGauge linkCountGauge;
    private final LongGauge heapUsedGauge;
    private final LongGauge heapMaxGauge;
    private final LongGauge threadCountGauge;
    private final LongGauge uptimeGauge;

    private final long startMillis = System.currentTimeMillis();

    public SystemMetrics(Meter meter) {
        this.linkCountGauge = meter.gaugeBuilder("linker.links.count")
                .setDescription("Current total number of short links stored")
                .setUnit("{link}")
                .ofLongs()
                .build();

        this.heapUsedGauge = meter.gaugeBuilder("linker.jvm.heap.used")
                .setDescription("JVM heap memory currently in use")
                .setUnit("By")
                .ofLongs()
                .build();

        this.heapMaxGauge = meter.gaugeBuilder("linker.jvm.heap.max")
                .setDescription("Maximum JVM heap memory available")
                .setUnit("By")
                .ofLongs()
                .build();

        this.threadCountGauge = meter.gaugeBuilder("linker.jvm.threads")
                .setDescription("Current number of live JVM threads")
                .setUnit("{thread}")
                .ofLongs()
                .build();

        this.uptimeGauge = meter.gaugeBuilder("linker.process.uptime")
                .setDescription("Time elapsed since the process started")
                .setUnit("s")
                .ofLongs()
                .build();
    }

    public void setLinkCount(long count) {
        linkCountGauge.set(count);
    }

    public void recordHeapUsage() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        heapUsedGauge.set(used);
        heapMaxGauge.set(runtime.maxMemory());
    }

    public void recordThreadCount() {
        threadCountGauge.set(Thread.activeCount());
    }

    public void recordUptime() {
        uptimeGauge.set((System.currentTimeMillis() - startMillis) / 1000);
    }
}
