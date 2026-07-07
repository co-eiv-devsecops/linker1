package linker.telemetry;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TelemetryTest {

    @Test
    void exposesTheGivenOpenTelemetryInstance() {
        var sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setMeterProvider(SdkMeterProvider.builder().build())
                .build();

        var telemetry = new Telemetry(sdk);

        assertSame(sdk, telemetry.openTelemetry());
    }

    @Test
    void exposesANonNullTracerAndMeter() {
        var sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().build())
                .setMeterProvider(SdkMeterProvider.builder().build())
                .build();

        var telemetry = new Telemetry(sdk);

        assertNotNull(telemetry.tracer());
        assertNotNull(telemetry.meter());
    }

    @Test
    void createFromEnvironmentBuildsAUsableInstance() {
        var telemetry = Telemetry.createFromEnvironment();

        assertNotNull(telemetry.openTelemetry());
        assertNotNull(telemetry.tracer());
        assertNotNull(telemetry.meter());
    }
}
