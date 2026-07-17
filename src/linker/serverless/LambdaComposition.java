package linker.serverless;

import linker.LinkRepository;
import linker.LinkService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Composition root for {@link LinkLambdaHandler}'s no-arg (real Lambda)
 * constructor -- wires a MySQL-backed {@link LinkService} from environment
 * variables. Mirrors {@code Main.class}'s role for the VM target and is
 * excluded from the JaCoCo coverage gate for the same reason: it opens a
 * real network connection, so there is no meaningful unit test for it
 * without a live database (see {@code test/linker/serverless/LinkLambdaHandlerTest.java}
 * for how the handler's actual logic is tested against an in-memory
 * connection instead).
 */
final class LambdaComposition {

    private LambdaComposition() {
    }

    static LinkService defaultService() {
        try {
            // Fat-jar assembly merges every dependency's
            // META-INF/services/java.sql.Driver with a "last one wins"
            // strategy, silently dropping MySQL's auto-registration in
            // favor of SQLite's -- same issue Main.java works around.
            Class.forName("com.mysql.cj.jdbc.Driver");
            var host = System.getenv().getOrDefault("MYSQL_HOST", "localhost");
            var database = System.getenv().getOrDefault("MYSQL_DATABASE", "");
            var user = System.getenv().getOrDefault("MYSQL_USER", "");
            var password = System.getenv().getOrDefault("MYSQL_PWD", "");
            var url = "jdbc:mysql://" + host + "/" + database;
            Connection conn = DriverManager.getConnection(url, user, password);
            try (var stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id VARCHAR(64) PRIMARY KEY, url TEXT)");
            }
            return new LinkService(new LinkRepository(conn));
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("Failed to initialize MySQL-backed LinkService for Lambda", e);
        }
    }
}
