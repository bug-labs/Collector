package xyz.bugsum.collector.gui;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import xyz.bugsum.collector.data.CollectorData;

@Getter
public class GUIHolder implements InventoryHolder {

    public enum Type { MAIN, UPGRADE, TOGGLE, XP, FILTER }

    private final Type type;
    private final CollectorData data;
    private Inventory inventory;
    @lombok.Setter private int page = 0;

    public GUIHolder(Type type, CollectorData data) {
        this.type = type;
        this.data = data;
    }

    public void create(int size, net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
