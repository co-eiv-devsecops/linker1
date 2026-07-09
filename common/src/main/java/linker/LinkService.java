package linker;

import java.net.URI;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

public class LinkService {

    private static final Set<String> RESERVED_ALIASES = Set.of("link", "app.js", "styles.css");

    private final LinkRepository repository;

    public LinkService(LinkRepository repository) {
        this.repository = repository;
    }

    public String get(String id) throws SQLException {
        return repository.findUrlById(id);
    }

    public Link create(String url) throws SQLException {
        return createResult(url).link();
    }

    public CreateResult createResult(String url) throws SQLException {
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }

        var existingId = repository.findIdByUrl(url);
        if (existingId != null) {
            return new CreateResult(new Link(existingId, url), false);
        }

        var id = repository.insertShortUrl(url);
        return new CreateResult(new Link(id, url), true);
    }

    public Link create(String url, String alias) throws SQLException {
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid URL: " + url);
        }

        if (alias == null) {
            return create(url);
        }

        if (!isValidAlias(alias)) {
            throw new IllegalArgumentException("Invalid alias: " + alias);
        }

        var existingUrlForAlias = repository.findUrlById(alias);
        if (existingUrlForAlias != null) {
            if (existingUrlForAlias.equals(url)) {
                return new Link(alias, url);
            }
            throw new AliasConflictException(alias);
        }

        repository.insertShortUrlWithId(alias, url);
        return new Link(alias, url);
    }

    public record CreateResult(Link link, boolean created) {
    }

    public static boolean isValidUrl(String url) {
        try {
            return new URI(url).isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isValidAlias(String alias) {
        if (alias == null || alias.isEmpty() || alias.length() > 64) {
            return false;
        }
        if (!alias.matches("[A-Za-z0-9_-]+")) {
            return false;
        }
        return !RESERVED_ALIASES.contains(alias);
    }

    public static String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}