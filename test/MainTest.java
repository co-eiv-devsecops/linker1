import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

class MainTest {

    @Test
    void mainBootsServerAndServesTheIndexPage() throws Exception {
        var dbFile = Files.createTempFile("linker1-main-test", ".db");
        dbFile.toFile().deleteOnExit();

        var process = startMainInSubprocess(dbFile.toAbsolutePath().toString(), "18099");
        try {
            waitForServer("http://localhost:18099/");

            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(URI.create("http://localhost:18099/")).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("<html"));
        } finally {
            process.destroy();
            process.waitFor();
        }
    }

    private static Process startMainInSubprocess(String dbPath, String port) throws IOException {
        var javaHome = System.getProperty("java.home");
        var javaBin = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java";
        var classpath = System.getProperty("java.class.path");

        var builder = new ProcessBuilder(javaBin, "-cp", classpath, "Main");
        builder.environment().put("LINKER_DB_PATH", dbPath);
        builder.environment().put("LINKER_PORT", port);
        builder.environment().put("LD_SDK_KEY", "test-key-for-testing");
        builder.environment().put("LD_OFFLINE", "true");
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private static void waitForServer(String url) throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create(url)).GET().build();

        for (int i = 0; i < 30; i++) {
            try {
                client.send(request, HttpResponse.BodyHandlers.discarding());
                return;
            } catch (Exception e) {
                Thread.sleep(200);
            }
        }
        fail("Server did not start in time");
    }
}
