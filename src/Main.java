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

        // Registered explicitly: the fat jar's assembly step merges every
        // dependency's META-INF/services/java.sql.Driver into one file with a
        // "last one wins" strategy, which silently drops the MySQL driver's
        // auto-registration in favor of SQLite's. Loading the class directly
        // triggers its static registration block regardless of that merge.
        Class.forName("com.mysql.cj.jdbc.Driver");
        Class.forName("org.sqlite.JDBC");
        var mysqlHost = System.getenv().getOrDefault("MYSQL_HOST", "localhost");
        var mysqlDatabase = System.getenv().getOrDefault("MYSQL_DATABASE", "");
        var mysqlUser = System.getenv().getOrDefault("MYSQL_USER", "");
        var mysqlPassword = System.getenv().getOrDefault("MYSQL_PWD", "");
        var mysqlUrl = "jdbc:mysql://" + mysqlHost + "/" + mysqlDatabase;

        // Links live in SQLite by default. Setting LINKER_STORAGE=mysql stores
        // them in the MySQL database above instead (e.g. AWS RDS), so they
        // survive blue/green redeploys that replace the VM.
        Connection conn;
        if ("mysql".equalsIgnoreCase(System.getenv("LINKER_STORAGE"))) {
            conn = DriverManager.getConnection(mysqlUrl + "?autoReconnect=true", mysqlUser, mysqlPassword);
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id VARCHAR(64) PRIMARY KEY, url TEXT)");
            }
            log.info("Database ready (storage=mysql host={})", mysqlHost);
        } else {
            var dbPath = System.getenv().getOrDefault("LINKER_DB_PATH", "linker1.db");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id TEXT PRIMARY KEY, url TEXT)");
            }
            log.debug("Database ready at path={}", dbPath);
        }

        int port = Integer.parseInt(
            System.getenv().getOrDefault("LINKER_PORT", "8080")
        );

        var repository = new LinkRepository(conn);
        repository.ensureClicksColumn();
        var service = new LinkService(repository);

        var requestMetrics = new RequestMetrics(telemetry.meter());
        var linkSpans = new LinkSpans(telemetry.tracer(), telemetry.meter());
        var systemMetrics = new SystemMetrics(telemetry.meter());

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

        // LaunchDarkly is optional: if the SDK key is missing or the service is
        // unreachable (e.g. an expired plan), run with default flag values
        // (classic UI) instead of refusing to start.
        String ldSdkKey = System.getenv("LD_SDK_KEY");
        LDClient ldClient = null;
        if (ldSdkKey == null || ldSdkKey.isBlank()) {
            System.err.println("WARN: LD_SDK_KEY is not set; feature flags will use their default values.");
        } else {
            System.out.println("Initializing LaunchDarkly...");
            try {
                if ("true".equalsIgnoreCase(System.getenv("LD_OFFLINE"))) {
                    com.launchdarkly.sdk.server.LDConfig config = new com.launchdarkly.sdk.server.LDConfig.Builder()
                        .offline(true)
                        .build();
                    ldClient = new LDClient(ldSdkKey, config);
                } else {
                    ldClient = new LDClient(ldSdkKey);
                }
                if (ldClient.isInitialized()) {
                    System.out.println("LaunchDarkly initialized successfully.");
                } else {
                    System.err.println("WARN: LaunchDarkly did not initialize; feature flags will use their default values.");
                }
            } catch (Exception e) {
                System.err.println("WARN: LaunchDarkly failed to start (" + e.getClass().getSimpleName()
                    + "); feature flags will use their default values.");
            }
        }

        final LDClient ldClientForShutdown = ldClient;
        FeatureFlags featureFlags = ldClient == null ? new FeatureFlags() : new FeatureFlags(ldClient);

        // Routes are registered before the server starts listening, so the port
        // never accepts a connection while it would otherwise 404 with no routes.
        var app = Javalin.create();
        new StaticRoutes(featureFlags).register(app);
        // Registered before LinkRoutes: GET /{id} would otherwise treat
        // "healthz" as a link id and shadow this exact-match route.
        new HealthRoutes(healthCheck).register(app);
        new LinkRoutes(service, requestMetrics, linkSpans).register(app);
        app.start(port);
        log.info("Server started on port={}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                telemetryScheduler.shutdown();
                if (ldClientForShutdown != null) {
                    ldClientForShutdown.close();
                }
                conn.close();
            } catch (Exception e) {
                log.error("Error during shutdown", e);
            }
        }));
    }
}
