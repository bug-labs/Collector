package me.iamsamarth.riftcollector.collector;

import me.iamsamarth.riftcollector.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The placeable collector item. A broken collector keeps its contents inside the item it drops:
 * a slot can hold a million items, and dropping those on the ground would mean thousands of
 * entities.
 */
public final class CollectorItem {

    private final NamespacedKey key;
    private final NamespacedKey slotsKey;
    private final NamespacedKey itemsKey;
    private final NamespacedKey amountsKey;
    private final Lang lang;

    public CollectorItem(Plugin plugin, Lang lang) {
        this.key = new NamespacedKey(plugin, "collector");
        this.slotsKey = new NamespacedKey(plugin, "contents_slots");
        this.itemsKey = new NamespacedKey(plugin, "contents_items");
        this.amountsKey = new NamespacedKey(plugin, "contents_amounts");
        this.lang = lang;
    }

    /** The material is passed in so a broken collector drops as the block it was placed as. */
    public ItemStack create(int amount, Material material) {
        ItemStack stack = new ItemStack(material, amount);
        stack.editMeta(meta -> {
            meta.displayName(lang.get("item.name"));
            meta.lore(lang.getList("item.lore"));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
        });
        return stack;
    }

    public ItemStack create(Material material, List<StoredStack> contents) {
        ItemStack stack = create(1, material);
        if (contents.isEmpty()) {
            return stack;
        }

        long total = 0;
        int[] slots = new int[contents.size()];
        long[] amounts = new long[contents.size()];
        List<ItemStack> prototypes = new ArrayList<>(contents.size());
        for (int index = 0; index < contents.size(); index++) {
            StoredStack stored = contents.get(index);
            slots[index] = index;
            amounts[index] = stored.amount();
            prototypes.add(stored.prototype());
            total += stored.amount();
        }

        long storedTotal = total;
        stack.editMeta(meta -> {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(slotsKey, PersistentDataType.INTEGER_ARRAY, slots);
            data.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(prototypes));
            data.set(amountsKey, PersistentDataType.LONG_ARRAY, amounts);

            List<Component> lore = new ArrayList<>(lang.getList("item.lore"));
            lore.add(lang.get("item.contents",
                    Placeholder.unparsed("items", String.format(Locale.ROOT, "%,d", storedTotal))));
            meta.lore(lore);
        });
        return stack;
    }

    /** What a dropped collector was carrying; empty for a fresh one. */
    public List<StoredStack> contents(ItemStack stack) {
        if (!isCollector(stack)) {
            return List.of();
        }
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        byte[] itemBytes = data.get(itemsKey, PersistentDataType.BYTE_ARRAY);
        long[] amounts = data.get(amountsKey, PersistentDataType.LONG_ARRAY);
        if (itemBytes == null || itemBytes.length == 0 || amounts == null || amounts.length == 0) {
            return List.of();
        }

        ItemStack[] prototypes = ItemStack.deserializeItemsFromBytes(itemBytes);
        List<StoredStack> contents = new ArrayList<>(prototypes.length);
        for (int index = 0; index < prototypes.length && index < amounts.length; index++) {
            if (amounts[index] > 0) {
                contents.add(new StoredStack(prototypes[index], amounts[index]));
            }
        }
        return contents;
    }

    public boolean isCollector(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }
}
