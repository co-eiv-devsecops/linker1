import io.javalin.Javalin;
import java.sql.*;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import linker.LinkRepository;
import linker.LinkService;
import linker.routes.LinkRoutes;
import linker.routes.StaticRoutes;

public class Main {
    public static void main(String[] args) throws Exception {
        var conn = DriverManager.getConnection("jdbc:sqlite:linker1.db");
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

        app.post("/link", ctx -> {
            var url = ctx.formParam("url");
            if (url == null && ctx.contentType() != null && ctx.contentType().contains("json")) {
                try {
                    var body = ctx.bodyAsClass(Map.class);
                    url = (String) body.get("url");
                } catch (Exception e) {
                    ctx.status(400).result("Invalid JSON");
                    return;
                }
            }
            if (url == null || !isValidUrl(url)) {
                ctx.status(400).result("Invalid URL");
                return;
            }

            var existing = findIdByUrl(conn, url);
            if (existing != null) {
                ctx.status(200).header("Location", "/" + existing).result(existing);
                return;
            }

            var id = insertShortUrl(conn, url);
            ctx.status(201).header("Location", "/" + id).result(id);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }));
    }

    static boolean isValidUrl(String url){
        try {
            return new URI(url).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    static String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    static String findIdByUrl(Connection conn, String url) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT id FROM shorturl WHERE url = ?")) {
            ps.setString(1, url);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
            return null;
        }
    }

    static String insertShortUrl(Connection conn, String url) throws SQLException {
        var id = generateId();
        try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, url);
            ps.executeUpdate();
        }
        return id;
    }
}
