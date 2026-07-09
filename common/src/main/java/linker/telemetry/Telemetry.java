package linker.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

public class Telemetry {

    public static final String INSTRUMENTATION_SCOPE = "linker1";

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final Meter meter;

    public Telemetry(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        this.meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE);
        OpenTelemetryAppender.install(openTelemetry);
    }

    public static Telemetry createFromEnvironment() {
        OpenTelemetry sdk = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
        return new Telemetry(sdk);
    }

    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    public Tracer tracer() {
        return tracer;
    }

    public Meter meter() {
        return meter;
    }
}