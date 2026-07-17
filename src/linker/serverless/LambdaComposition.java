package linker.serverless;

import linker.LinkRepository;
import linker.LinkService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Composition root for {@link LinkLambdaHandler}'s no-arg (real Lambda)
 * constructor. Mirrors {@code Main.class}'s role for the VM target and is
 * excluded from the JaCoCo coverage gate for the same reason: it opens a
 * real database connection, so there is no meaningful unit test for it
 * without live infrastructure (see {@code test/linker/serverless/LinkLambdaHandlerTest.java}
 * for how the handler's actual logic is tested against an in-memory
 * connection instead).
 *
 * <p>Two datastore modes, chosen by whether {@code MYSQL_HOST} is set:
 * <ul>
 *   <li><b>MySQL</b> (when {@code MYSQL_HOST} is set) -- the documented
 *       production design (see docs/SERVERLESS.md). Requires the Lambda to
 *       have network reachability to that MySQL host (a VPC config, or a
 *       publicly reachable managed MySQL).</li>
 *   <li><b>SQLite in {@code /tmp}</b> (when {@code MYSQL_HOST} is unset) --
 *       a self-contained fallback with no external database dependency, used
 *       for the demo deployment in an environment where no MySQL is reachable
 *       (e.g. a cross-cloud gap: the VM target's MySQL lives in OCI's private
 *       network, unreachable from AWS Lambda). Lambda's {@code /tmp} is
 *       writable but ephemeral and not shared across concurrent execution
 *       environments, so data does not persist reliably across cold starts --
 *       fine for a functional demo, not for production (which is exactly why
 *       the production design uses MySQL).</li>
 * </ul>
 */
final class LambdaComposition {

    private LambdaComposition() {
    }

    static LinkService defaultService() {
        var host = System.getenv("MYSQL_HOST");
        Connection conn = (host == null || host.isBlank())
                ? sqliteFallback()
                : mysql(host);
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS shorturl (id VARCHAR(64) PRIMARY KEY, url TEXT)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure the shorturl table exists", e);
        }
        return new LinkService(new LinkRepository(conn));
    }

    private static Connection mysql(String host) {
        try {
            // Fat-jar assembly merges every dependency's
            // META-INF/services/java.sql.Driver with a "last one wins"
            // strategy, silently dropping MySQL's auto-registration in
            // favor of SQLite's -- same issue Main.java works around.
            Class.forName("com.mysql.cj.jdbc.Driver");
            var database = System.getenv().getOrDefault("MYSQL_DATABASE", "");
            var user = System.getenv().getOrDefault("MYSQL_USER", "");
            var password = System.getenv().getOrDefault("MYSQL_PWD", "");
            var url = "jdbc:mysql://" + host + "/" + database;
            return DriverManager.getConnection(url, user, password);
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("Failed to initialize MySQL-backed LinkService for Lambda", e);
        }
    }

    private static Connection sqliteFallback() {
        try {
            // /tmp is the only writable path in the Lambda execution
            // environment; sqlite-jdbc also extracts its native library there.
            return DriverManager.getConnection("jdbc:sqlite:/tmp/linker1.db");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize SQLite-backed LinkService for Lambda", e);
        }
    }
}
