package me.iamsamarth.riftcollector.storage;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A collector as it is stored. Items arrive here already serialised, because turning an
 * ItemStack into bytes is Bukkit work and has to happen on the main thread, while writing the
 * row happens on the database thread.
 */
public record CollectorRecord(UUID id,
                              UUID world,
                              int x,
                              int y,
                              int z,
                              @Nullable UUID owner,
                              @Nullable String ownerName,
                              String material,
                              double earnings,
                              long itemsSold,
                              long createdAt,
                              List<SlotRecord> slots,
                              Map<String, Integer> upgrades,
                              Map<String, String> settings,
                              Set<String> filter,
                              Map<String, String> trusted) {

    public record SlotRecord(int slot, byte[] item, long amount) {
    }
}
