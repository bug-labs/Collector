package me.iamsamarth.riftcollector.gui;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Locale;

/** The menu state that conditions and placeholders are resolved against. */
public record MenuContext(int page, int totalPages, int usedSlots, int capacity, long itemCount) {

    public boolean hasNextPage() {
        return page < totalPages;
    }

    public boolean hasPreviousPage() {
        return page > 1;
    }

    public TagResolver placeholders() {
        return TagResolver.resolver(
                Placeholder.unparsed("page", Integer.toString(page)),
                Placeholder.unparsed("pages", Integer.toString(totalPages)),
                Placeholder.unparsed("next-page", Integer.toString(Math.min(page + 1, totalPages))),
                Placeholder.unparsed("previous-page", Integer.toString(Math.max(page - 1, 1))),
                Placeholder.unparsed("stored", Integer.toString(usedSlots)),
                Placeholder.unparsed("capacity", Integer.toString(capacity)),
                Placeholder.unparsed("free", Integer.toString(Math.max(capacity - usedSlots, 0))),
                Placeholder.unparsed("items", String.format(Locale.ROOT, "%,d", itemCount))
        );
    }
}
