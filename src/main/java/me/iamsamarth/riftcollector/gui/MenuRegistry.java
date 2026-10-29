package me.iamsamarth.riftcollector.gui;

import me.iamsamarth.riftcollector.collector.Collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The menus that are currently open. Several players can watch the same collector, so whatever
 * changes it has to tell their menus to redraw.
 */
public final class MenuRegistry {

    private final Map<UUID, List<Menu>> openByCollector = new HashMap<>();

    void opened(Menu menu) {
        openByCollector.computeIfAbsent(menu.collector().id(), key -> new ArrayList<>()).add(menu);
    }

    void closed(Menu menu) {
        List<Menu> menus = openByCollector.get(menu.collector().id());
        if (menus != null && menus.remove(menu) && menus.isEmpty()) {
            openByCollector.remove(menu.collector().id());
        }
    }

    public void refresh(Collector collector) {
        List<Menu> menus = openByCollector.get(collector.id());
        if (menus == null) {
            return;
        }
        for (Menu menu : List.copyOf(menus)) {
            menu.refresh();
        }
    }

    public void closeFor(Collector collector) {
        List<Menu> menus = openByCollector.remove(collector.id());
        if (menus == null) {
            return;
        }
        for (Menu menu : List.copyOf(menus)) {
            menu.close();
        }
    }

    public void closeAll() {
        for (List<Menu> menus : List.copyOf(openByCollector.values())) {
            for (Menu menu : List.copyOf(menus)) {
                menu.close();
            }
        }
        openByCollector.clear();
    }
}
