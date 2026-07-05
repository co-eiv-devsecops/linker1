import io.javalin.Javalin;
import java.sql.*;

import linker.LinkRepository;
import linker.LinkService;
import linker.config.FeatureFlags;
import linker.routes.LinkRoutes;
import linker.routes.StaticRoutes;
import com.launchdarkly.sdk.server.LDClient;

public class Main {

    public static void main(String[] args) throws Exception {
        var dbPath = System.getenv().getOrDefault("LINKER_DB_PATH", "linker1.db");
        var conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }

        int port = Integer.parseInt(
            System.getenv().getOrDefault("LINKER_PORT", "8080")
        );

        var app = Javalin.create().start(port);

        var repository = new LinkRepository(conn);
        var service = new LinkService(repository);


        String ldSdkKey = System.getenv("LD_SDK_KEY");
        if (ldSdkKey == null || ldSdkKey.isBlank()) {
            System.err.println("ERROR: La variable de entorno LD_SDK_KEY no está configurada.");
            System.exit(1);
        }

        System.out.println("Inicializando LaunchDarkly...");
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
            System.err.println("ERROR: Falló la inicialización del cliente de LaunchDarkly.");
            System.exit(1);
        }
        System.out.println("LaunchDarkly inicializado correctamente.");

        FeatureFlags featureFlags = new FeatureFlags(ldClient);
        new StaticRoutes(featureFlags).register(app);
        new LinkRoutes(service).register(app);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ldClient.close();
                conn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}
