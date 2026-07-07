package linker.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;

/**
 * RED-method metrics (Rate, Errors, Duration) for HTTP requests, following the
 * counter/histogram naming and unit conventions from the OpenTelemetry semantic
 * conventions for HTTP servers.
 *
 * <p>Callers never build {@link io.opentelemetry.api.metrics.Meter} instruments
 * themselves -- they call {@link #recordRequest(String, int, long)} once per
 * request, which is the entire integration surface.
 */
public class RequestMetrics {

    private final LongCounter requestCounter;
    private final LongCounter errorCounter;
    private final LongHistogram requestDuration;

    public RequestMetrics(Meter meter) {
        this.requestCounter = meter.counterBuilder("linker.http.requests")
                .setDescription("Total number of HTTP requests handled")
                .setUnit("{request}")
                .build();

        this.errorCounter = meter.counterBuilder("linker.http.errors")
                .setDescription("Total number of HTTP requests that resulted in an error response (>=400)")
                .setUnit("{request}")
                .build();

        this.requestDuration = meter.histogramBuilder("linker.http.request.duration")
                .setDescription("Duration of HTTP requests")
                .setUnit("ms")
                .ofLongs()
                .build();
    }

    public void recordRequest(String route, int statusCode, long durationMillis) {
        Attributes attributes = Attributes.builder()
                .put("route", route)
                .put("status_code", statusCode)
                .build();

        requestCounter.add(1, attributes);
        requestDuration.record(durationMillis, attributes);
        if (statusCode >= 400) {
            errorCounter.add(1, attributes);
        }
    }
}
