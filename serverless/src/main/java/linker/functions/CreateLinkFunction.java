package linker.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import linker.AliasConflictException;
import linker.LinkRepository;
import linker.LinkService;
import linker.telemetry.LinkSpans;
import linker.telemetry.Telemetry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

public class CreateLinkFunction {

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
            throw new RuntimeException("Failed to initialize CreateLinkFunction", e);
        }
    }

    @FunctionName("CreateLink")
    public HttpResponseMessage create(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "link"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Creating link");

        try {
            String body = request.getBody().orElse("");
            String url = request.getQueryParameters().getOrDefault("url", "");
            String alias = request.getQueryParameters().getOrDefault("alias", null);

            if (url.isEmpty() && !body.isEmpty()) {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var json = mapper.readTree(body);
                url = json.has("url") ? json.get("url").asText() : "";
                alias = json.has("alias") ? json.get("alias").asText() : null;
            }

            if (url.isEmpty()) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("URL is required")
                    .build();
            }

            String finalUrl = url;
            String finalAlias = alias;

            if (alias != null && !alias.isEmpty()) {
                var link = linkSpans.traceCreate(url, () -> service.create(finalUrl, finalAlias));
                return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Location", "/" + link.id())
                    .body(link.id())
                    .build();
            }

            var result = linkSpans.traceCreate(url, () -> service.createResult(finalUrl));
            var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
            return request.createResponseBuilder(status)
                .header("Location", "/" + result.link().id())
                .body(result.link().id())
                .build();

        } catch (AliasConflictException e) {
            return request.createResponseBuilder(HttpStatus.CONFLICT)
                .body(e.getMessage())
                .build();
        } catch (IllegalArgumentException e) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body(e.getMessage())
                .build();
        } catch (Exception e) {
            context.getLogger().severe("Error creating link: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal error")
                .build();
        }
    }
}