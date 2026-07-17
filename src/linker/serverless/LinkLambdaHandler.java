package linker.serverless;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import linker.AliasConflictException;
import linker.Link;
import linker.LinkRepository;
import linker.LinkService;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;

/**
 * AWS Lambda entry point for the serverless deployment target (issue #74).
 *
 * <p>Reuses {@link LinkService}/{@link LinkRepository} unchanged -- the same
 * classes the VM deployment's {@code linker.routes.LinkRoutes} delegates to
 * -- via API Gateway's proxy integration instead of an embedded Javalin/Jetty
 * server, which Lambda doesn't need (API Gateway already terminates HTTP).
 *
 * <p>MySQL is the datastore here, not SQLite: Lambda's filesystem is
 * ephemeral and not shared across invocations/instances, so a file-backed
 * SQLite database would not persist reliably. {@link LinkRepository} takes
 * any {@code java.sql.Connection}, so this requires no changes to it -- only
 * a different connection source than the VM target uses. This does not
 * change what the VM deployment does; MYSQL_* here is a separate,
 * serverless-only concern (see docs/SERVERLESS.md).
 *
 * <p>The real MySQL wiring lives in {@link LambdaComposition} (excluded from
 * the JaCoCo coverage gate, same rationale as {@code Main.class}: a
 * composition root that opens a real network connection has no meaningful
 * unit test without a live database). Routing/business logic here stays
 * fully unit-testable via the package-visible constructor.
 */
public class LinkLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LinkService service;

    /** No-arg constructor Lambda actually invokes: builds a real MySQL-backed service from environment variables. */
    @Generated
    public LinkLambdaHandler() {
        this(LambdaComposition.defaultService());
    }

    /** Package-visible constructor for tests: injects a service against a fake/in-memory connection. */
    LinkLambdaHandler(LinkService service) {
        this.service = service;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String method = request.getHttpMethod();
        String id = pathId(request);

        try {
            if ("POST".equals(method) && "/link".equals(request.getPath())) {
                return handleCreate(request);
            }
            if (id == null) {
                return response(404, "Not found");
            }
            return switch (method) {
                case "GET" -> handleGet(id);
                case "HEAD" -> handleHead(id);
                case "DELETE" -> handleDelete(id);
                default -> response(405, "Method not allowed");
            };
        } catch (Exception e) {
            return response(500, "Internal error");
        }
    }

    private String pathId(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        if (pathParams != null && pathParams.get("id") != null) {
            return pathParams.get("id");
        }
        String path = request.getPath();
        if (path == null || path.equals("/") || path.equals("/link")) {
            return null;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private APIGatewayProxyResponseEvent handleGet(String id) throws SQLException {
        String url = service.get(id);
        if (url == null) {
            return response(404, "Not found");
        }
        return redirect(url);
    }

    private APIGatewayProxyResponseEvent handleHead(String id) throws SQLException {
        String url = service.get(id);
        if (url == null) {
            return response(404, "Not found");
        }
        return response(200, url);
    }

    private APIGatewayProxyResponseEvent handleDelete(String id) throws SQLException {
        boolean deleted = service.delete(id);
        return deleted ? response(204, "") : response(404, "Not found");
    }

    private APIGatewayProxyResponseEvent handleCreate(APIGatewayProxyRequestEvent request) throws SQLException {
        String url;
        String alias;
        try {
            Map<?, ?> body = MAPPER.readValue(request.getBody() == null ? "{}" : request.getBody(), Map.class);
            url = (String) body.get("url");
            alias = (String) body.get("alias");
        } catch (Exception e) {
            return response(400, "Invalid JSON");
        }

        try {
            if (alias != null) {
                Link link = service.create(url, alias);
                return created(link.id());
            }
            LinkService.CreateResult result = service.createResult(url);
            return result.created() ? created(result.link().id()) : ok(result.link().id());
        } catch (AliasConflictException e) {
            return response(409, e.getMessage());
        } catch (IllegalArgumentException e) {
            return response(400, e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent redirect(String location) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(301)
                .withHeaders(Map.of("Location", location));
    }

    private APIGatewayProxyResponseEvent created(String id) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(201)
                .withHeaders(Map.of("Location", "/" + id))
                .withBody(id);
    }

    private APIGatewayProxyResponseEvent ok(String id) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Map.of("Location", "/" + id))
                .withBody(id);
    }

    private APIGatewayProxyResponseEvent response(int status, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Collections.emptyMap())
                .withBody(body);
    }
}
