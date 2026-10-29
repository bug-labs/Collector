package me.iamsamarth.riftcollector.gui;

import me.iamsamarth.riftcollector.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A chest menu described by a YAML file in the gui/ directory: a character grid plus the item
 * each character stands for. Content slots are the ones paginated contents are drawn into.
 */
public final class MenuLayout {

    private static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    private static final Map<ClickType, StackAction> DEFAULT_CONTENT_CLICKS = Map.of(
            ClickType.LEFT, StackAction.TAKE_STACK,
            ClickType.RIGHT, StackAction.TAKE_ONE,
            ClickType.SHIFT_LEFT, StackAction.FILL_INVENTORY);

    private final Lang lang;
    private final String title;
    private final int size;
    private final int[] contentSlots;
    private final Map<Integer, Integer> contentIndexBySlot;
    private final Map<Integer, MenuIcon> iconsBySlot;
    private final Map<ClickType, StackAction> contentClicks;
    private final List<String> contentLore;
    private final Map<Character, MenuIcon> iconsByCharacter;
    private final YamlConfiguration source;

    private MenuLayout(Lang lang, String title, int size, int[] contentSlots, Map<Integer, MenuIcon> iconsBySlot,
                       Map<ClickType, StackAction> contentClicks, List<String> contentLore,
                       Map<Character, MenuIcon> iconsByCharacter, YamlConfiguration source) {
        this.lang = lang;
        this.title = title;
        this.size = size;
        this.contentSlots = contentSlots;
        this.iconsBySlot = iconsBySlot;
        this.contentClicks = contentClicks;
        this.contentLore = contentLore;
        this.iconsByCharacter = iconsByCharacter;
        this.source = source;
        this.contentIndexBySlot = new HashMap<>(contentSlots.length);
        for (int index = 0; index < contentSlots.length; index++) {
            contentIndexBySlot.put(contentSlots[index], index);
        }
    }

    public static MenuLayout load(File file, Lang lang, Logger logger) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int rows = Math.clamp(config.getInt("rows", MAX_ROWS), 1, MAX_ROWS);
        String title = config.getString("title", "");
        char contentCharacter = firstCharacter(config.getString("content-character", "#"), '#');

        Map<Character, MenuIcon> iconsByCharacter = loadIcons(config.getConfigurationSection("items"), logger);
        List<String> grid = config.getStringList("layout");
        List<Integer> contentSlots = new ArrayList<>();
        Map<Integer, MenuIcon> iconsBySlot = new HashMap<>();

        for (int row = 0; row < rows; row++) {
            String line = row < grid.size() ? grid.get(row) : "";
            for (int column = 0; column < COLUMNS; column++) {
                char character = column < line.length() ? line.charAt(column) : ' ';
                int slot = row * COLUMNS + column;
                if (character == contentCharacter) {
                    contentSlots.add(slot);
                } else if (character != ' ') {
                    MenuIcon icon = iconsByCharacter.get(character);
                    if (icon == null) {
                        logger.warning("Layout character '" + character + "' in " + file.getName()
                                + " has no item defined, leaving slot " + slot + " empty.");
                        continue;
                    }
                    iconsBySlot.put(slot, icon);
                }
            }
        }

        if (contentSlots.isEmpty()) {
            logger.warning(file.getName() + " has no '" + contentCharacter
                    + "' content slots; collector contents cannot be shown.");
        }
        return new MenuLayout(lang, title, rows * COLUMNS,
                contentSlots.stream().mapToInt(Integer::intValue).toArray(), iconsBySlot,
                loadContentClicks(config.getConfigurationSection("content-clicks"), file.getName(), logger),
                List.copyOf(config.getStringList("content-item.lore")),
                iconsByCharacter, config);
    }

    private static Map<ClickType, StackAction> loadContentClicks(@Nullable ConfigurationSection section,
                                                                 String fileName, Logger logger) {
        if (section == null) {
            return DEFAULT_CONTENT_CLICKS;
        }
        Map<ClickType, StackAction> clicks = new EnumMap<>(ClickType.class);
        for (String key : section.getKeys(false)) {
            ClickType click = readClickType(key);
            StackAction action = StackAction.parse(section.getString(key, "none"));
            if (click == null) {
                logger.warning("Unknown click '" + key + "' under content-clicks in " + fileName + ".");
                continue;
            }
            if (action == null) {
                logger.warning("Unknown action '" + section.getString(key) + "' for click '" + key
                        + "' in " + fileName + ".");
                continue;
            }
            clicks.put(click, action);
        }
        return clicks.isEmpty() ? DEFAULT_CONTENT_CLICKS : clicks;
    }

    private static @Nullable ClickType readClickType(String raw) {
        try {
            return ClickType.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Map<Character, MenuIcon> loadIcons(@Nullable ConfigurationSection section, Logger logger) {
        if (section == null) {
            return Map.of();
        }
        Map<Character, MenuIcon> icons = new HashMap<>();
        Map<Character, Character> fallbackCharacters = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null || key.isEmpty()) {
                continue;
            }
            char character = key.charAt(0);
            MenuIcon icon = MenuIcon.load(itemSection, character, logger);
            if (icon == null) {
                continue;
            }
            icons.put(character, icon);
            String fallback = itemSection.getString("fallback");
            if (fallback != null && !fallback.isEmpty()) {
                fallbackCharacters.put(character, fallback.charAt(0));
            }
        }

        // Fallbacks reference other icons by character, so they can only be resolved once every
        // icon is known. One level deep: a fallback never falls back itself.
        fallbackCharacters.forEach((character, fallbackCharacter) -> {
            MenuIcon fallback = icons.get(fallbackCharacter);
            if (fallback == null) {
                logger.warning("Menu item '" + character + "' falls back to unknown item '"
                        + fallbackCharacter + "', the slot will be empty instead.");
                return;
            }
            icons.computeIfPresent(character, (unused, icon) -> icon.withFallback(fallback));
        });
        return icons;
    }

    public int[] contentSlots() {
        return contentSlots.clone();
    }

    public StackAction contentAction(ClickType click) {
        return contentClicks.getOrDefault(click, StackAction.NONE);
    }

    /** Extra lore lines drawn under every stored stack, with &lt;amount&gt; available. */
    public List<String> contentLore() {
        return contentLore;
    }

    public int contentSlotCount() {
        return contentSlots.length;
    }

    /** Index of a slot within the content area, or -1 when the slot is not a content slot. */
    public int contentIndexOf(int slot) {
        return contentIndexBySlot.getOrDefault(slot, -1);
    }

    public @Nullable MenuIcon iconAt(int slot, MenuContext context) {
        MenuIcon icon = iconsBySlot.get(slot);
        return icon == null ? null : icon.visible(context);
    }

    /** An icon by its layout character, for menus that choose between icons themselves. */
    public @Nullable MenuIcon icon(String character) {
        return character == null || character.isEmpty() ? null : iconsByCharacter.get(character.charAt(0));
    }

    /** A menu file may hold sections only its own menu understands, such as upgrade slots. */
    public @Nullable ConfigurationSection section(String path) {
        return source.getConfigurationSection(path);
    }

    /** A single value from the menu file, for options only one menu cares about. */
    public @Nullable String option(String path) {
        return source.getString(path);
    }

    public Lang lang() {
        return lang;
    }

    public Inventory create(InventoryHolder holder, MenuContext context) {
        return Bukkit.createInventory(holder, size, lang.parse(title, context.placeholders()));
    }

    public void decorate(Inventory inventory, MenuContext context, TagResolver extra) {
        iconsBySlot.forEach((slot, icon) -> {
            MenuIcon visible = icon.visible(context);
            inventory.setItem(slot, visible == null ? null : visible.render(context, lang, extra));
        });
    }

    private static char firstCharacter(String raw, char fallback) {
        return raw.isEmpty() ? fallback : raw.charAt(0);
    }
}
