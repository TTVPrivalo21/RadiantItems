package dev.privalo.items.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.privalo.items.RadiantItems;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous update checker (GitHub Releases or SpigotMC through Spiget).
 * Runs off the main thread with a 5 s timeout; any network/HTTP/parse problem is ignored silently
 * (only logged at FINE level, never with a stack trace).
 */
public final class UpdateChecker implements Listener {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** What the last successful check found. */
    public record Result(String installed, String latest, String url, String source) {
    }

    private final RadiantItems plugin;
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Result available;

    public UpdateChecker(RadiantItems plugin) {
        this.plugin = plugin;
    }

    /** Update found by the last check, or null when up to date / unknown. */
    public Result available() {
        return available;
    }

    /** Starts a new asynchronous check (called on enable and on /ri reload). */
    public void check() {
        int run = generation.incrementAndGet();
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("update-checker.enabled", true)) {
            available = null;
            return;
        }
        String source = config.getString("update-checker.source", "github").trim().toLowerCase(Locale.ROOT);
        String installed = plugin.getPluginMeta().getVersion();
        String userAgent = "RadiantItems/" + installed + " (update-checker)";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        CompletableFuture<Result> future;
        if (source.equals("spigot")) {
            int id = config.getInt("update-checker.spigot-resource-id", 0);
            if (id <= 0) {
                plugin.getLogger().fine("update-checker: source=spigot pero spigot-resource-id es 0; comprobación omitida.");
                return;
            }
            URI api = URI.create("https://api.spiget.org/v2/resources/" + id + "/versions/latest");
            String download = "https://www.spigotmc.org/resources/" + id + "/";
            future = fetch(client, api, userAgent, "application/json").thenApply(json -> {
                String name = string(json, "name");
                return name == null ? null : new Result(installed, Versions.normalize(name), download, "SpigotMC");
            });
        } else {
            String repo = config.getString("update-checker.github-repo", "").trim();
            if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || repo.startsWith("TU_USUARIO/")) {
                plugin.getLogger().fine("update-checker: github-repo vacío o inválido ('" + repo + "'); comprobación omitida.");
                return;
            }
            URI api = URI.create("https://api.github.com/repos/" + repo + "/releases/latest");
            String fallback = "https://github.com/" + repo + "/releases/latest";
            future = fetch(client, api, userAgent, "application/vnd.github+json").thenApply(json -> {
                String tag = string(json, "tag_name");
                String url = string(json, "html_url");
                return tag == null ? null : new Result(installed, Versions.normalize(tag),
                        url != null && url.startsWith("https://") ? url : fallback, "GitHub");
            });
        }
        future.whenComplete((result, error) -> {
            if (run != generation.get()) {
                return;
            }
            if (error != null || result == null) {
                plugin.getLogger().fine("update-checker: no se pudo comprobar ("
                        + (error == null ? "respuesta sin versión" : rootMessage(error)) + ").");
                return;
            }
            if (!Versions.isNewer(result.latest(), installed)) {
                available = null;
                plugin.getLogger().fine("update-checker: RadiantItems " + installed + " está al día (última: " + result.latest() + ").");
                return;
            }
            available = result;
            Bukkit.getScheduler().runTask(plugin, () -> announce(result));
        });
    }

    /** GET with timeout + User-Agent; completes exceptionally for any non-200 answer (404 = no releases yet). */
    private static CompletableFuture<JsonElement> fetch(HttpClient client, URI uri, String userAgent, String accept) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(TIMEOUT.toSeconds() * 2, java.util.concurrent.TimeUnit.SECONDS)
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    return JsonParser.parseString(response.body());
                });
    }

    private static String string(JsonElement json, String key) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }
        JsonObject object = json.getAsJsonObject();
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        String text = value.getAsString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private void announce(Result result) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getLogger().warning("============================================================");
        plugin.getLogger().warning(" ¡Hay una nueva versión de RadiantItems disponible! (" + result.source() + ")");
        plugin.getLogger().warning("   Versión instalada : " + result.installed());
        plugin.getLogger().warning("   Nueva versión     : " + result.latest());
        plugin.getLogger().warning("   Descarga          : " + result.url());
        plugin.getLogger().warning("============================================================");
        if (plugin.getConfig().getBoolean("update-checker.notify-admins-on-join", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                notify(player, result);
            }
        }
    }

    private void notify(Player player, Result result) {
        if (!player.hasPermission("radiantitems.admin")) {
            return;
        }
        plugin.messages().send(player, "update-available", "installed", result.installed(), "latest", result.latest(),
                "url", result.url(), "source", result.source());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Result result = available;
        if (result == null || !plugin.getConfig().getBoolean("update-checker.enabled", true)
                || !plugin.getConfig().getBoolean("update-checker.notify-admins-on-join", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("radiantitems.admin")) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                notify(player, result);
            }
        }, 60L);
    }
}
