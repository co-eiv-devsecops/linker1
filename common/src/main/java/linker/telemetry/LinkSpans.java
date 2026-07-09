package linker.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

public class LinkSpans {

    @FunctionalInterface
    public interface ThrowingSupplier<T, E extends Exception> {
        T get() throws E;
    }

    private final Tracer tracer;
    private final LongHistogram dbOperationDuration;

    public LinkSpans(Tracer tracer, Meter meter) {
        this.tracer = tracer;
        this.dbOperationDuration = meter.histogramBuilder("linker.db.operation.duration")
                .setDescription("Duration of repository operations backing link creation/resolution")
                .setUnit("ms")
                .ofLongs()
                .build();
    }

    public <T, E extends Exception> T traceCreate(String url, ThrowingSupplier<T, E> persistWork) throws E {
        Span parent = tracer.spanBuilder("link.create")
                .setAttribute("link.url", url)
                .startSpan();
        try (Scope parentScope = parent.makeCurrent()) {
            return runChildSpan("link.create.persist", "create", persistWork, parent);
        } finally {
            parent.end();
        }
    }

    public <T, E extends Exception> T traceResolve(String id, ThrowingSupplier<T, E> lookupWork) throws E {
        Span parent = tracer.spanBuilder("link.resolve")
                .setAttribute("link.id", id)
                .startSpan();
        try (Scope parentScope = parent.makeCurrent()) {
            return runChildSpan("link.resolve.lookup", "resolve", lookupWork, parent);
        } finally {
            parent.end();
        }
    }

    private <T, E extends Exception> T runChildSpan(String spanName, String operation, ThrowingSupplier<T, E> work, Span parent) throws E {
        Span child = tracer.spanBuilder(spanName).startSpan();
        long start = System.currentTimeMillis();
        try (Scope childScope = child.makeCurrent()) {
            T result = work.get();
            child.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            child.recordException(e);
            child.setStatus(StatusCode.ERROR, e.getMessage());
            parent.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            dbOperationDuration.record(System.currentTimeMillis() - start,
                    Attributes.builder().put("operation", operation).build());
            child.end();
        }
    }
}