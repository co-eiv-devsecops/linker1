package linker.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

/**
 * Traces the two user-facing operations of the link shortener: creating a link
 * and resolving one. Each is a single public method that wraps a whole
 * operation in a parent span and a validation/persistence step in a nested
 * child span, so a caller gets a full trace by making one method call instead
 * of hand-rolling span lifecycles. The duration of that child span (the actual
 * repository call) is also recorded in the {@code linker.db.operation.duration}
 * histogram, tagged by operation name.
 */
public class LinkSpans {

    /** A unit of work that may throw a checked exception, e.g. a repository call. */
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

    /**
     * Traces link creation as a parent "link.create" span containing a child
     * "link.create.persist" span around the actual repository/service call.
     */
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

    /**
     * Traces link resolution as a parent "link.resolve" span containing a
     * child "link.resolve.lookup" span around the actual repository lookup.
     */
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
