package org.maks.fishingPlugin.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

public class AntiCheatServiceTest {

  @Test
  void serializeDeserializePreservesSamples() {
    AntiCheatService ac = new AntiCheatService(3, 5);
    UUID id = UUID.randomUUID();
    long base = 1000L;
    ac.record(id, base + 100); // first click
    ac.record(id, base + 200); // interval 100
    ac.record(id, base + 302); // interval 102
    byte[] data = ac.serialize(id);

    AntiCheatService ac2 = new AntiCheatService(3, 5);
    ac2.deserialize(id, data);
    assertTrue(ac2.record(id, base + 400)); // interval 98 -> low variance
  }
}
