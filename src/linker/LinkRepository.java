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
}
