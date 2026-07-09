package linker.functions;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import linker.health.HealthCheck;
import linker.health.HealthCheckMetrics;
import linker.telemetry.Telemetry;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

public class HealthFunction {

    private static HealthCheck healthCheck;

    static {
        try {
            var telemetry = Telemetry.createFromEnvironment();
            Class.forName("com.mysql.cj.jdbc.Driver");
            var mysqlHost = System.getenv().getOrDefault("MYSQL_HOST", "localhost");
            var mysqlDatabase = System.getenv().getOrDefault("MYSQL_DATABASE", "");
            var mysqlUser = System.getenv().getOrDefault("MYSQL_USER", "");
            var mysqlPassword = System.getenv().getOrDefault("MYSQL_PWD", "");
            var mysqlUrl = "jdbc:mysql://" + mysqlHost + "/" + mysqlDatabase;
            Supplier<Connection> mysqlConnectionSupplier = () -> {
                try {
                    return DriverManager.getConnection(mysqlUrl, mysqlUser, mysqlPassword);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            };
            var healthCheckMetrics = new HealthCheckMetrics(telemetry.meter());
            healthCheck = new HealthCheck(telemetry.tracer(), mysqlConnectionSupplier, healthCheckMetrics);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HealthFunction", e);
        }
    }

    @FunctionName("HealthCheck")
    public HttpResponseMessage health(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.GET},
                authLevel = AuthorizationLevel.ANONYMOUS,
                route = "healthz"
            ) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {

        context.getLogger().info("Health check requested");

        var result = healthCheck.check();
        if (result.healthy()) {
            return request.createResponseBuilder(HttpStatus.OK)
                .body("OK")
                .build();
        }
        return request.createResponseBuilder(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Unhealthy: " + result.detail())
            .build();
    }
}