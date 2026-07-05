error id: file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/Main.java:_empty_/StaticRoutes#
file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/Main.java
empty definition using pc, found symbol in pc: _empty_/StaticRoutes#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 960
uri: file:///D:/ander/Documents/INTERSEMESTRAL/Software%20Resiliente/Clase%201/linker1/src/Main.java
text:
```scala
import io.javalin.Javalin;
import java.sql.*;

import linker.LinkRepository;
import linker.LinkService;
import linker.config.FeatureFlags;
import linker.routes.LinkRoutes;
import linker.routes.StaticRoutes;

public class Main {

    FeatureFlags featureFlags = new FeatureFlags();

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

        @@StaticRoutes.register(app);
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

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/StaticRoutes#