package xyz.bugsum.collector.utils;

/**
 * ASCII → Unicode small-caps mapping used for runtime placeholders (player names, custom
 * Collector names) so they render consistently next to the small-caps lore text in the UI.
 *
 * Tags wrapped in &lt;...&gt; are left untouched so MiniMessage parsing isn't broken.
 */
public final class SmallCaps {

    private SmallCaps() {}

    private static final char[] LOWER = {
        'ᴀ','ʙ','ᴄ','ᴅ','ᴇ','ғ','ɢ','ʜ','ɪ','ᴊ',
        'ᴋ','ʟ','ᴍ','ɴ','ᴏ','ᴘ','ǫ','ʀ','ѕ','ᴛ',
        'ᴜ','ᴠ','ᴡ','x','ʏ','ᴢ'
    };

    /** Convert any ASCII letters (both cases) in {@code s} to Unicode small caps. */
    public static String of(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder(s.length());
        boolean inTag = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') { inTag = true; out.append(c); continue; }
            if (c == '>') { inTag = false; out.append(c); continue; }
            if (inTag) { out.append(c); continue; }
            if (c >= 'A' && c <= 'Z')      out.append(LOWER[c - 'A']);
            else if (c >= 'a' && c <= 'z') out.append(LOWER[c - 'a']);
            else                            out.append(c);
        }
        return out.toString();
    }
}
