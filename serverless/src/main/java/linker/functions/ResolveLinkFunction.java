package linker.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import linker.LinkRepository;
import linker.LinkService;
import linker.telemetry.LinkSpans;
import linker.telemetry.Telemetry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

public class ResolveLinkFunction {

    private static Telemetry telemetry;
    private static LinkService service;
    private static LinkSpans linkSpans;

    static {
        try {
            telemetry = Telemetry.createFromEnvironment();
            var dbPath = System.getenv().getOrDefault("LINKER_DB_PATH", "linker1.db");
            var conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            var repository = new LinkRepository(conn);
            service = new LinkService(repository);
            linkSpans = new LinkSpans(telemetry.tracer(), telemetry.meter());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ResolveLinkFunction", e);
        }
    }

    @FunctionName("ResolveLink")
    public HttpResponseMessage resolve(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "{id}"
            ) HttpRequestMessage<Optional<String>> request,
            @BindingName("id") String id,
            final ExecutionContext context) {

        context.getLogger().info("Resolving link: " + id);

        try {
            String url = linkSpans.traceResolve(id, () -> service.get(id));
            if (url != null) {
                return request.createResponseBuilder(HttpStatus.MOVED_PERMANENTLY)
                    .header("Location", url)
                    .build();
            }
            return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                .body("Not found")
                .build();
        } catch (Exception e) {
            context.getLogger().severe("Error resolving link: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal error")
                .build();
        }
    }
}