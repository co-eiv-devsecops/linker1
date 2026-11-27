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

    public void insertShortUrlWithId(String id, String url) throws SQLException {
        try (var ps = conn.prepareStatement("INSERT INTO shorturl (id, url) VALUES (?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, url);
            ps.executeUpdate();
        }
    }

    public long countLinks() throws SQLException {
        try (var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM shorturl")) {
            rs.next();
            return rs.getLong("total");
        }
    }

    public boolean delete(String id) throws SQLException {
        try (var ps = conn.prepareStatement("DELETE FROM shorturl WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Migración idempotente: agrega clicks a bases creadas antes de esta HU. */
    public void ensureClicksColumn() throws SQLException {
        try (var rs = conn.getMetaData().getColumns(null, null, "shorturl", "clicks")) {
            if (rs.next()) {
                return;
            }
        }
        try (var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE shorturl ADD COLUMN clicks INTEGER NOT NULL DEFAULT 0");
        }
    }

    /** Suma una apertura en SQL (atómico). Devuelve false si el id no existe. */
    public boolean incrementClicks(String id) throws SQLException {
        try (var ps = conn.prepareStatement("UPDATE shorturl SET clicks = clicks + 1 WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Devuelve el conteo, o null si el id no existe (null != 0). */
    public Long findClicksById(String id) throws SQLException {
        try (var ps = conn.prepareStatement("SELECT clicks FROM shorturl WHERE id = ?")) {
            ps.setString(1, id);
            var rs = ps.executeQuery();
            return rs.next() ? rs.getLong("clicks") : null;
        }
    }
}