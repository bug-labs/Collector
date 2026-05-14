package xyz.bugsum.collector.listeners;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import xyz.bugsum.collector.Collector;
import xyz.bugsum.collector.config.MessagesConfig;
import xyz.bugsum.collector.data.CollectorData;
import xyz.bugsum.collector.data.FilterData;
import xyz.bugsum.collector.gui.FilterGUI;
import xyz.bugsum.collector.gui.GUIHolder;
import xyz.bugsum.collector.gui.MainGUI;
import xyz.bugsum.collector.gui.TogglesGUI;
import xyz.bugsum.collector.gui.UpgradesGUI;
import xyz.bugsum.collector.gui.XpGUI;
import xyz.bugsum.collector.hooks.EconomyShopHook;
import xyz.bugsum.collector.managers.EconomyManager;
import xyz.bugsum.collector.managers.StorageManager;
import xyz.bugsum.collector.managers.UpgradeManager;
import xyz.bugsum.collector.managers.XpBankManager;

import java.util.HashSet;
import java.util.Set;

public class GUIListener implements Listener {

    private final Collector plugin;
    private static final Set<Integer> MAIN_STORAGE_SET = new HashSet<>();
    static {
        for (int s : MainGUI.STORAGE_SLOTS) MAIN_STORAGE_SET.add(s);
    }

    public GUIListener(Collector plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof xyz.bugsum.collector.gui.AdminGUI.Holder) {
            xyz.bugsum.collector.gui.AdminGUI.handleClick(event);
            return;
        }
        if (!(top.getHolder() instanceof GUIHolder holder)) return;

        switch (holder.getType()) {
            case MAIN    -> handleMainClick(event, holder);
            case UPGRADE -> handleUpgradesClick(event, holder);
            case TOGGLE  -> handleTogglesClick(event, holder);
            case XP      -> handleXpClick(event, holder);
            case FILTER  -> handleFilterClick(event, holder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIHolder.Type.FILTER) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize() && slot >= FilterGUI.CONTROL_ROW_START) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() == GUIHolder.Type.FILTER) {
            FilterGUI.persist(top, holder.getData().getFilter());
            holder.getData().markDirty();
            plugin.getPluginManager().getDatabase().enqueueSave(holder.getData());
        }
    }

    /* ----------------- Main GUI ----------------- */

    private void handleMainClick(InventoryClickEvent event, GUIHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        CollectorData data = holder.getData();

        if (MAIN_STORAGE_SET.contains(slot)) {
            ItemStack shown = event.getCurrentItem();
            if (shown == null || shown.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) return;
            handleStorageItemClick(p, holder, data, shown.getType(), event.getClick());
            return;
        }

        switch (slot) {
            case MainGUI.SLOT_UPGRADES -> {
                if (!checkPerm(p, "collector.upgrade")) return;
                UpgradesGUI.open(p, data);
            }
            case MainGUI.SLOT_TOGGLES -> {
                if (!checkPerm(p, "collector.toggles")) return;
                TogglesGUI.open(p, data);
            }
            case MainGUI.SLOT_XP -> {
                if (!checkPerm(p, "collector.xp")) return;
                XpGUI.open(p, data);
            }
            case MainGUI.SLOT_INFO -> { /* display-only */ }
            case MainGUI.SLOT_PREV -> {
                if (holder.getPage() > 0) {
                    holder.setPage(holder.getPage() - 1);
                    MainGUI.render(holder.getInventory(), data, holder.getPage(), p);
                }
            }
            case MainGUI.SLOT_NEXT -> {
                int totalPages = MainGUI.pageCount(data);
                if (holder.getPage() < totalPages - 1) {
                    holder.setPage(holder.getPage() + 1);
                    MainGUI.render(holder.getInventory(), data, holder.getPage(), p);
                }
            }
            case MainGUI.SLOT_CLOSE -> p.closeInventory();
            default -> {}
        }
    }

    private void handleStorageItemClick(Player p, GUIHolder holder, CollectorData data,
                                        Material mat, ClickType click) {
        StorageManager storage = plugin.getPluginManager().getStorage();
        switch (click) {
            case LEFT -> storage.takeOneStack(data, p, mat);
            case SHIFT_LEFT -> storage.takeMax(data, p, mat);
            case RIGHT -> storage.giveToPlayer(data, p, mat, 1);
            case SHIFT_RIGHT -> sellMaterial(p, data, mat);
            default -> {}
        }
        MainGUI.refresh(p, data);
    }

    /* ----------------- Upgrades GUI ----------------- */

    private void handleUpgradesClick(InventoryClickEvent event, GUIHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        CollectorData data = holder.getData();

        switch (event.getSlot()) {
            case UpgradesGUI.SLOT_SPEED    -> attemptUpgrade(p, data, UpgradeManager.Track.SPEED);
            case UpgradesGUI.SLOT_RANGE    -> attemptUpgrade(p, data, UpgradeManager.Track.RANGE);
            case UpgradesGUI.SLOT_CAPACITY -> attemptUpgrade(p, data, UpgradeManager.Track.CAPACITY);
            case UpgradesGUI.SLOT_BACK     -> MainGUI.open(p, data);
            default -> {}
        }
    }

    private void attemptUpgrade(Player p, CollectorData data, UpgradeManager.Track track) {
        if (!checkPerm(p, "collector.upgrade." + track.name().toLowerCase())) return;
        boolean ok = plugin.getPluginManager().getUpgrades().attemptUpgrade(p, data, track);
        if (ok) plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> UpgradesGUI.refresh(p, data), 1L);
    }

    /* ----------------- Toggles GUI ----------------- */

    private void handleTogglesClick(InventoryClickEvent event, GUIHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        CollectorData data = holder.getData();

        switch (event.getSlot()) {
            case TogglesGUI.SLOT_AUTOSELL -> {
                if (!checkPerm(p, "collector.toggle.autosell")) return;
                plugin.getPluginManager().getUpgrades().toggleAutosell(p, data);
                TogglesGUI.refresh(p, data);
            }
            case TogglesGUI.SLOT_AUTOSMELT -> {
                if (!checkPerm(p, "collector.toggle.autosmelt")) return;
                plugin.getPluginManager().getUpgrades().toggleAutosmelt(p, data);
                TogglesGUI.refresh(p, data);
            }
            case TogglesGUI.SLOT_XP -> {
                if (!checkPerm(p, "collector.toggle.xp")) return;
                plugin.getPluginManager().getUpgrades().toggleXp(p, data);
                TogglesGUI.refresh(p, data);
            }
            case TogglesGUI.SLOT_HOLOGRAM -> {
                if (!checkPerm(p, "collector.toggle.hologram")) return;
                data.setHologramVisible(!data.isHologramVisible());
                plugin.getPluginManager().getHolograms().toggle(data);
                data.markDirty();
                plugin.getPluginManager().getDatabase().enqueueSave(data);
                MessagesConfig msg = plugin.getPluginManager().getMessages();
                p.sendMessage(msg.get(data.isHologramVisible() ? "upgrade.toggled-on" : "upgrade.toggled-off",
                    "feature", "Hologram"));
                TogglesGUI.refresh(p, data);
            }
            case TogglesGUI.SLOT_RENAME -> openRenamePrompt(p, data);
            case TogglesGUI.SLOT_FRIENDS -> openFriendPrompt(p, data);
            case TogglesGUI.SLOT_FILTER -> {
                if (!checkPerm(p, "collector.filter")) return;
                FilterGUI.open(p, data);
            }
            case TogglesGUI.SLOT_BACK -> MainGUI.open(p, data);
            default -> {}
        }
    }

    /* ----------------- XP GUI ----------------- */

    private void handleXpClick(InventoryClickEvent event, GUIHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        CollectorData data = holder.getData();
        XpBankManager bank = plugin.getPluginManager().getXpBank();

        switch (event.getSlot()) {
            case XpGUI.SLOT_WITHDRAW_1 -> {
                if (!checkPerm(p, "collector.xp.withdraw")) return;
                long withdrawn = bank.withdrawOneLevel(p, data);
                if (withdrawn > 0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                }
                XpGUI.refresh(p, data);
            }
            case XpGUI.SLOT_WITHDRAW_ALL -> {
                if (!checkPerm(p, "collector.xp.withdraw")) return;
                long withdrawn = bank.withdrawAll(p, data);
                if (withdrawn > 0) {
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
                }
                XpGUI.refresh(p, data);
            }
            case XpGUI.SLOT_BACK -> MainGUI.open(p, data);
            default -> {}
        }
    }

    /* ----------------- Filter GUI ----------------- */

    private void handleFilterClick(InventoryClickEvent event, GUIHolder holder) {
        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        boolean inTop = rawSlot < top.getSize();
        boolean inControlRow = inTop && rawSlot >= FilterGUI.CONTROL_ROW_START;

        if (inControlRow) {
            event.setCancelled(true);
            var modeBtn = plugin.getPluginManager().getGui().getFilterModeButton();
            if (modeBtn != null && rawSlot == modeBtn.getSlot()) {
                cycleMode(holder.getData().getFilter());
                FilterGUI.renderControls(top, plugin.getPluginManager().getGui(), holder.getData().getFilter());
                if (event.getWhoClicked() instanceof Player p) {
                    MessagesConfig msg = plugin.getPluginManager().getMessages();
                    p.sendMessage(switch (holder.getData().getFilter().getMode()) {
                        case WHITELIST -> msg.get("filter.whitelist");
                        case BLACKLIST -> msg.get("filter.blacklist");
                        case NONE -> msg.get("filter.none");
                    });
                }
            }
        }
    }

    private void cycleMode(FilterData filter) {
        filter.setMode(switch (filter.getMode()) {
            case NONE -> FilterData.Mode.WHITELIST;
            case WHITELIST -> FilterData.Mode.BLACKLIST;
            case BLACKLIST -> FilterData.Mode.NONE;
        });
    }

    private void sellMaterial(Player p, CollectorData data, Material mat) {
        EconomyManager eco = plugin.getPluginManager().getEconomy();
        EconomyShopHook esg = plugin.getPluginManager().getEconomyShop();
        MessagesConfig msg = plugin.getPluginManager().getMessages();

        if (!eco.isAvailable()) {
            p.sendMessage(msg.get("economy.unavailable"));
            return;
        }
        long amount = data.getStorage().getOrDefault(mat, 0L);
        if (amount <= 0) return;

        ItemStack one = new ItemStack(mat, 1);
        double unit;
        if (esg.isAvailable()) {
            unit = esg.sellPrice(one);
            if (Double.isNaN(unit) || unit <= 0) unit = lookupYaml(mat);
        } else {
            unit = lookupYaml(mat);
        }
        if (unit <= 0 || Double.isNaN(unit)) {
            p.sendMessage(msg.get("economy.unavailable"));
            return;
        }
        double total = unit * amount;
        eco.deposit(p.getUniqueId(), total);
        data.getStorage().remove(mat);
        data.setMoneyEarned(data.getMoneyEarned() + total);
        data.markDirty();
        p.sendMessage(msg.get("economy.sold", "amount", String.format("%,.2f", total)));
    }

    private double lookupYaml(Material mat) {
        Double yaml = plugin.getPluginManager().getTiers().getSellPrices().get(mat);
        return yaml == null ? Double.NaN : yaml;
    }

    private void openRenamePrompt(Player p, CollectorData data) {
        if (!checkPerm(p, "collector.rename")) return;
        if (!isOwnerOrAdmin(p, data)) {
            p.sendMessage(plugin.getPluginManager().getMessages().get("break.not-owner"));
            return;
        }
        String current = data.getDisplayName() == null ? "Collector" : data.getDisplayName();
        xyz.bugsum.collector.gui.AnvilInputGUI.prompt(p, "Rename Collector", current, input -> {
            String trimmed = input == null ? "" : input.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("Collector") || trimmed.equalsIgnoreCase("reset")) {
                data.setDisplayName(null);
            } else {
                data.setDisplayName(trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed);
            }
            data.markDirty();
            plugin.getPluginManager().getDatabase().enqueueSave(data);
            plugin.getPluginManager().getHolograms().updateText(data);
            p.sendMessage(plugin.getPluginManager().getMessages().get("rename.success",
                "name", data.getDisplayName() == null ? "Collector" : data.getDisplayName()));
            plugin.getServer().getScheduler().runTask(plugin, () -> TogglesGUI.open(p, data));
        });
    }

    private void openFriendPrompt(Player p, CollectorData data) {
        if (!checkPerm(p, "collector.friend.manage")) return;
        if (!isOwnerOrAdmin(p, data)) {
            p.sendMessage(plugin.getPluginManager().getMessages().get("break.not-owner"));
            return;
        }
        xyz.bugsum.collector.gui.AnvilInputGUI.prompt(p, "Friend Name (toggle add/remove)", "", input -> {
            if (input == null || input.isBlank()) return;
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(input.trim());
            if (target.getUniqueId() == null) {
                p.sendMessage(plugin.getPluginManager().getMessages().get("friend.unknown", "name", input));
                return;
            }
            var fm = plugin.getPluginManager().getFriends();
            if (fm.has(data, target.getUniqueId())) {
                fm.remove(data, target.getUniqueId());
                p.sendMessage(plugin.getPluginManager().getMessages().get("friend.removed", "name", input));
            } else {
                fm.add(data, target.getUniqueId());
                p.sendMessage(plugin.getPluginManager().getMessages().get("friend.added", "name", input));
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> TogglesGUI.open(p, data));
        });
    }

    private boolean isOwnerOrAdmin(Player p, CollectorData data) {
        return (data.getOwner() != null && data.getOwner().equals(p.getUniqueId()))
            || p.hasPermission("collector.admin");
    }

    /** Silent perm check — UI buttons already advertise their perm requirement in lore via
     *  {@link xyz.bugsum.collector.utils.GuiPerm}, so no chat spam on misclicks. */
    private boolean checkPerm(Player p, String perm) {
        return p.hasPermission(perm);
    }
}
