package org.maks.fishingPlugin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.maks.fishingPlugin.data.Profile;
import org.maks.fishingPlugin.data.ProfileRepo;

public class LevelServiceTest {

    @Test
    void addQsEarnedUpdatesProfile() throws SQLException {
        ProfileRepo repo = mock(ProfileRepo.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        LevelService service = new LevelService(repo, plugin);

        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(repo.find(id)).thenReturn(Optional.empty());

        service.loadProfile(player);
        service.addQsEarned(player, 50L);
        service.saveProfile(player);

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(repo).upsert(captor.capture());
        assertEquals(50L, captor.getValue().qsEarned());
    }
}
