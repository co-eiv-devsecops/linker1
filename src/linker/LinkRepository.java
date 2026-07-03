package linker;

import java.sql.Connection;
import java.sql.SQLException;

public class LinkRepository {

    private final Connection conn;

    public LinkRepository(Connection conn) {
        this.conn = conn;
    }

    public String findUrlById(String id) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT url FROM shorturl WHERE id = ?")) {
            ps.setString(1, id);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("url");
            }
            return null;
        }
    }

    public String findIdByUrl(String url) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT id FROM shorturl WHERE url = ?")) {
            ps.setString(1, url);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
            return null;
        }
    }

    public String insertShortUrl(String url) throws SQLException {
        var id = LinkService.generateId();
        insertShortUrlWithId(id, url);
        return id;
    }

    public boolean existsById(String id) throws SQLException {
        return findUrlById(id) != null;
    }

    public void insertShortUrlWithId(String id, String url) throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, url);
            ps.executeUpdate();
        }
    }
}
