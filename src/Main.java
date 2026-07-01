import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.sql.*;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws Exception {
        var conn = DriverManager.getConnection("jdbc:sqlite:linker1.db");
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id TEXT PRIMARY KEY, url TEXT)");
        }

        int port = Integer.parseInt(
            System.getenv().getOrDefault("LINKER_PORT", "8080")
        );

        var app = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/";
                staticFiles.location = Location.CLASSPATH;
            });
        }).start(port);

        app.get("/", ctx -> ctx.redirect("/index.html"));

        app.get("/{id}", ctx -> {
            var id = ctx.pathParam("id");
            try (var ps = conn.prepareStatement("SELECT url FROM shorturl WHERE id = ?")) {
                ps.setString(1, id);
                var rs = ps.executeQuery();
                if (rs.next()) {
                    ctx.redirect(rs.getString("url"), HttpStatus.MOVED_PERMANENTLY);
                } else {
                    ctx.status(404).result("Not found");
                }
            }
        });

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

            try (var ps = conn.prepareStatement("SELECT id FROM shorturl WHERE url = ?")) {
                ps.setString(1, url);
                var rs = ps.executeQuery();
                if (rs.next()) {
                    ctx.status(200).header("Location", "/" + rs.getString("id")).result(rs.getString("id"));
                    return;
                }
            }

            var id = generateId();
            try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
                ps.setString(1, id);
                ps.setString(2, url);
                ps.executeUpdate();
            }
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
}