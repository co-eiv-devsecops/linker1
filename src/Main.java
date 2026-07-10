import io.javalin.Javalin;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import linker.LinkRepository;
import linker.LinkService;
import linker.config.FeatureFlags;
import linker.health.HealthCheck;
import linker.health.HealthCheckMetrics;
import linker.health.HealthRoutes;
import linker.routes.LinkRoutes;
import linker.routes.StaticRoutes;
import linker.telemetry.LinkSpans;
import linker.telemetry.RequestMetrics;
import linker.telemetry.SystemMetrics;
import linker.telemetry.Telemetry;
import com.launchdarkly.sdk.server.LDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        Telemetry telemetry = Telemetry.createFromEnvironment();
        log.info("Telemetry initialized (service={})", Telemetry.INSTRUMENTATION_SCOPE);

        var dbPath = System.getenv().getOrDefault("LINKER_DB_PATH", "linker1.db");
        var conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }
        log.debug("Database ready at path={}", dbPath);

        int port = Integer.parseInt(
            System.getenv().getOrDefault("LINKER_PORT", "8080")
        );

        var repository = new LinkRepository(conn);
        var service = new LinkService(repository);

        var requestMetrics = new RequestMetrics(telemetry.meter());
        var linkSpans = new LinkSpans(telemetry.tracer(), telemetry.meter());
        var systemMetrics = new SystemMetrics(telemetry.meter());

        // Registered explicitly: the fat jar's assembly step merges every
        // dependency's META-INF/services/java.sql.Driver into one file with a
        // "last one wins" strategy, which silently drops the MySQL driver's
        // auto-registration in favor of SQLite's. Loading the class directly
        // triggers its static registration block regardless of that merge.
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
        var healthCheck = new HealthCheck(telemetry.tracer(), mysqlConnectionSupplier, healthCheckMetrics);

        var telemetryScheduler = Executors.newSingleThreadScheduledExecutor();
        telemetryScheduler.scheduleAtFixedRate(() -> {
            try {
                systemMetrics.recordHeapUsage();
                systemMetrics.recordThreadCount();
                systemMetrics.recordUptime();
                systemMetrics.setLinkCount(repository.countLinks());
            } catch (Exception e) {
                log.warn("Failed to sample system metrics", e);
            }
        }, 0, 30, TimeUnit.SECONDS);

        String ldSdkKey = System.getenv("LD_SDK_KEY");
        if (ldSdkKey == null || ldSdkKey.isBlank()) {
            System.err.println("ERROR: The LD_SDK_KEY environment variable is not set.");
            System.exit(1);
        }

        System.out.println("Initializing LaunchDarkly...");
        LDClient ldClient;
        if ("true".equalsIgnoreCase(System.getenv("LD_OFFLINE"))) {
            com.launchdarkly.sdk.server.LDConfig config = new com.launchdarkly.sdk.server.LDConfig.Builder()
                .offline(true)
                .build();
            ldClient = new LDClient(ldSdkKey, config);
        } else {
            ldClient = new LDClient(ldSdkKey);
        }

        if (!ldClient.isInitialized()) {
            System.err.println("ERROR: Failed to initialize the LaunchDarkly client.");
            System.exit(1);
        }
        System.out.println("LaunchDarkly initialized successfully.");

        FeatureFlags featureFlags = new FeatureFlags(ldClient);

        // Javalin 7 requires every route to be registered inside the config block,
        // before the server exists -- registering routes on a live Javalin instance
        // after creation (the Javalin 6 pattern) is no longer supported.
        var app = Javalin.create(config -> {
            new StaticRoutes(featureFlags).register(config.routes);
            // Registered before LinkRoutes: GET /{id} would otherwise treat
            // "healthz" as a link id and shadow this exact-match route.
            new HealthRoutes(healthCheck).register(config.routes);
            new LinkRoutes(service, requestMetrics, linkSpans).register(config.routes);
        });
        app.start(port);
        log.info("Server started on port={}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                telemetryScheduler.shutdown();
                ldClient.close();
                conn.close();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }));
    }
}
