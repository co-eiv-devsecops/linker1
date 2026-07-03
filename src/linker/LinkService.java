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

    public Link create(String url) throws SQLException {
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }

        var existingId = repository.findIdByUrl(url);
        if (existingId != null) {
            return new Link(existingId, url);
        }

        var id = repository.insertShortUrl(url);
        return new Link(id, url);
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
