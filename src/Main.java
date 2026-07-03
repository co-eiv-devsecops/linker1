import io.javalin.Javalin;
import java.sql.*;

import linker.LinkRepository;
import linker.LinkService;
import linker.routes.LinkRoutes;
import linker.routes.StaticRoutes;

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

        StaticRoutes.register(app);
        LinkRoutes.register(app, service);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }));
    }
}
