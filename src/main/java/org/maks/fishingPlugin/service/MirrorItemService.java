package org.maks.fishingPlugin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.maks.fishingPlugin.model.MirrorItem;

/** In-memory repository of mirror items. */
public class MirrorItemService {

  private final Map<String, MirrorItem> byKey = new HashMap<>();

  /** Register a mirror item. */
  public void add(MirrorItem item) {
    byKey.put(item.key(), item);
  }

  /** Retrieve a mirror item by key. */
  public MirrorItem get(String key) {
    return byKey.get(key);
  }

  /** Expose registered items. */
  public List<MirrorItem> getAll() {
    return List.copyOf(byKey.values());
  }
}
