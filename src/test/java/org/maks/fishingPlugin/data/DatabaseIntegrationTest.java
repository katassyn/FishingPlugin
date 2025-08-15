package org.maks.fishingPlugin.data;

import static org.junit.jupiter.api.Assertions.*;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.maks.fishingPlugin.model.MirrorItem;
import org.maks.fishingPlugin.model.Category;

/** Integration test verifying MySQL migrations and data access. */
public class DatabaseIntegrationTest {

    private static DB db;
    private static Database database;

    @BeforeAll
    static void startDb() throws Exception {
        DBConfigurationBuilder config = DBConfigurationBuilder.newBuilder();
        config.setPort(0); // random available port
        db = DB.newEmbeddedDB(config.build());
        db.start();
        String jdbcUrl = "jdbc:mysql://localhost:" + db.getConfiguration().getPort() + "/test";
        database = new Database(jdbcUrl, "root", "");
        DataSource ds = database.getDataSource();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @AfterAll
    static void stopDb() throws Exception {
        if (database != null) {
            database.close();
        }
        if (db != null) {
            db.stop();
        }
    }

    @Test
    void testProfileUpsertAndFind() throws SQLException {
        DataSource ds = database.getDataSource();
        ProfileRepo repo = new ProfileRepo(ds);
        UUID id = UUID.randomUUID();
        Profile profile = new Profile(id, 5, 10L, 2L, 1500L, 800L);
        repo.upsert(profile);
        Optional<Profile> loaded = repo.find(id);
        assertTrue(loaded.isPresent());
        assertEquals(profile, loaded.get());
    }

    @Test
    void testMirrorItemUpsert() throws SQLException {
        DataSource ds = database.getDataSource();
        MirrorItemRepo repo = new MirrorItemRepo(ds);
        MirrorItem item = new MirrorItem("key1", Category.FISH, true, "data");
        repo.upsert(item);
        java.util.List<MirrorItem> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals(item, all.get(0));

        MirrorItem updated = new MirrorItem("key1", Category.TREASURE, false, "data2");
        repo.upsert(updated);
        all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals(updated, all.get(0));
    }
}
