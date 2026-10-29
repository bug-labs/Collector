package me.iamsamarth.riftcollector.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.iamsamarth.riftcollector.config.PluginConfig;
import me.iamsamarth.riftcollector.lang.Lang;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks Modrinth whether a newer build exists, on its own thread, and tells the console plus
 * anyone joining who is allowed to hear about it.
 */
public final class UpdateChecker implements Listener {

    private static final String API = "https://api.modrinth.com/v2/project/%s/version";
    private static final List<String> LOADERS = List.of("paper", "spigot", "bukkit", "purpur", "folia");
    private static final Pattern NUMBERS = Pattern.compile("\\d+");
    private static final Pattern PRE_RELEASE = Pattern.compile(".*[^0-9.].*");

    private final Plugin plugin;
    private final Lang lang;
    private final String currentVersion;

    private PluginConfig.Updates settings;
    private @Nullable BukkitTask task;
    private volatile @Nullable String available;

    public UpdateChecker(Plugin plugin, Lang lang, PluginConfig.Updates settings) {
        this.plugin = plugin;
        this.lang = lang;
        this.settings = settings;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    public void start(PluginConfig.Updates settings) {
        this.settings = settings;
        stop();
        if (!settings.enabled() || settings.project().isBlank()) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::check, 100L, settings.checkIntervalTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String latest = available;
        if (latest == null || !settings.notifyOnJoin()
                || !event.getPlayer().hasPermission(settings.notifyPermission())) {
            return;
        }
        lang.send(event.getPlayer(), "update.available",
                Placeholder.unparsed("current", currentVersion),
                Placeholder.unparsed("latest", latest));
    }

    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API.formatted(settings.project())))
                    .header("User-Agent", "RiftCollector/" + currentVersion)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().fine("Update check returned HTTP " + response.statusCode() + ".");
                return;
            }

            String latest = newest(JsonParser.parseString(response.body()).getAsJsonArray());
            if (latest == null || !isNewer(latest, currentVersion)) {
                return;
            }
            if (!latest.equals(available)) {
                available = latest;
                plugin.getLogger().info("RiftCollector " + latest + " is available; running "
                        + currentVersion + ".");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            // A failed update check is never worth a stack trace in the console.
            plugin.getLogger().fine("Update check failed: " + exception.getMessage());
        }
    }

    /** Modrinth lists versions newest first, so the first one that fits this server wins. */
    private @Nullable String newest(JsonArray versions) {
        String serverVersion = plugin.getServer().getMinecraftVersion();
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!contains(version, "game_versions", serverVersion) || !matchesLoader(version)) {
                continue;
            }
            JsonElement number = version.get("version_number");
            if (number != null) {
                return number.getAsString();
            }
        }
        return null;
    }

    private boolean matchesLoader(JsonObject version) {
        JsonElement loaders = version.get("loaders");
        if (loaders == null || !loaders.isJsonArray()) {
            return false;
        }
        for (JsonElement loader : loaders.getAsJsonArray()) {
            if (LOADERS.contains(loader.getAsString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(JsonObject version, String field, String value) {
        JsonElement element = version.get(field);
        if (element == null || !element.isJsonArray()) {
            return false;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.getAsString().equals(value)) {
                return true;
            }
        }
        return false;
    }

    /** Compares the numbers in both versions, so 1.2.10 counts as newer than 1.2.9. */
    static boolean isNewer(String candidate, String current) {
        int[] left = numbers(candidate);
        int[] right = numbers(current);
        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            int candidatePart = index < left.length ? left[index] : 0;
            int currentPart = index < right.length ? right[index] : 0;
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        // Same numbers: only a finished release replacing a pre-release build is newer, so
        // 1.0.0 beats 1.0.0-dev while 1.0.0-dev never beats 1.0.0.
        return preRelease(current) && !preRelease(candidate);
    }

    /** Every run of digits, in order, so a prefix such as "v1.3.0" does not shift the parts. */
    private static int[] numbers(String version) {
        Matcher matcher = NUMBERS.matcher(version);
        List<Integer> parts = new ArrayList<>();
        while (matcher.find()) {
            String group = matcher.group();
            parts.add(group.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(group));
        }
        int[] numbers = new int[parts.size()];
        for (int index = 0; index < numbers.length; index++) {
            numbers[index] = parts.get(index);
        }
        return numbers;
    }

    private static boolean preRelease(String version) {
        return PRE_RELEASE.matcher(version).matches();
    }
}
