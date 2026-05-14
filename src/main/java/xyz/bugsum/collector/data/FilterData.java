package xyz.bugsum.collector.data;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class FilterData {

    public enum Mode { NONE, WHITELIST, BLACKLIST }

    @Getter @Setter private Mode mode = Mode.NONE;
    @Getter private final Set<Material> items = new LinkedHashSet<>();

    public boolean accepts(Material m) {
        return switch (mode) {
            case NONE -> true;
            case WHITELIST -> items.contains(m);
            case BLACKLIST -> !items.contains(m);
        };
    }

    public String serializeItems() {
        return items.stream().map(Material::name).collect(Collectors.joining(","));
    }

    public void deserializeItems(String s) {
        items.clear();
        if (s == null || s.isEmpty()) return;
        for (String token : s.split(",")) {
            Material m = Material.matchMaterial(token.trim());
            if (m != null) items.add(m);
        }
    }
}
