package linker;

import java.net.URI;
import java.sql.SQLException;
import java.util.UUID;

public class LinkService {

    private final LinkRepository repository;

    public LinkService(LinkRepository repository) {
        this.repository = repository;
    }

    public String get(String id) throws SQLException {
        return repository.findUrlById(id);
    }

    public static boolean isValidUrl(String url) {
        try {
            return new URI(url).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    public static String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
