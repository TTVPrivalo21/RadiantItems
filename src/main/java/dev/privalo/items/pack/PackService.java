package dev.privalo.items.pack;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.util.Text;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** Owns the generated pack, the embedded HTTP server and delivery to players. */
public final class PackService implements Listener {

    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("radiantitems:resource-pack".getBytes(StandardCharsets.UTF_8));

    private final RadiantItems plugin;
    private final PackBuilder builder;
    private final PackHttpServer http;
    private final Set<UUID> notifyOnLoad = ConcurrentHashMap.newKeySet();
    private volatile BuiltPack current;
    private volatile boolean building;
    private String baseUrl = "";
    private long textureStamp;

    public PackService(RadiantItems plugin) {
        this.plugin = plugin;
        this.builder = new PackBuilder(plugin.getDataFolder(), plugin.getLogger());
        this.http = new PackHttpServer(plugin.getLogger());
    }

    public void ensureFolders() {
        builder.ensureStructure();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("resource-pack.enabled", false);
    }

    // ───────────────────────── HTTP ─────────────────────────

    public void startHttp() {
        FileConfiguration config = plugin.getConfig();
        if (!enabled() || !config.getBoolean("http.enabled", true)) {
            http.stop();
            baseUrl = "";
            return;
        }
        int port = config.getInt("http.port", 8166);
        http.start(config.getString("http.bind", "0.0.0.0"), port, config.getInt("http.threads", 2), name -> {
            BuiltPack pack = current;
            return pack != null && pack.fileName().equals(name) ? pack : null;
        });
        String configured = config.getString("http.public-url", "");
        if (configured != null && !configured.isBlank()) {
            baseUrl = configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        } else {
            baseUrl = "http://" + detectHost() + ":" + port;
            plugin.getLogger().info("URL automática del pack: " + baseUrl + "  (configura http.public-url si los jugadores no pueden descargarlo)");
        }
    }

    private static String detectHost() {
        String ip = Bukkit.getIp();
        if (ip != null && !ip.isBlank() && !ip.equals("0.0.0.0")) {
            return ip;
        }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("192.0.2.1"), 9);
            InetAddress local = socket.getLocalAddress();
            if (local != null && !local.isAnyLocalAddress()) {
                return local.getHostAddress();
            }
        } catch (Exception ignored) {
            // falls back to the local host address below
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }

    public void stop() {
        http.stop();
    }

    // ───────────────────────── build ─────────────────────────

    private static String parent(CustomItem def) {
        if (def.model != null) {
            return def.model.toLowerCase(Locale.ROOT);
        }
        String m = def.material;
        boolean tool = m.endsWith("_SWORD") || m.endsWith("_PICKAXE") || m.endsWith("_AXE") || m.endsWith("_SHOVEL")
                || m.endsWith("_HOE") || m.equals("STICK") || m.equals("BLAZE_ROD") || m.equals("BREEZE_ROD")
                || m.equals("FISHING_ROD") || m.equals("TRIDENT") || m.equals("MACE") || m.equals("BONE");
        return tool ? "handheld" : "generated";
    }

    /** Regenerates the pack asynchronously; completes on the main thread with true when the SHA-1 changed. */
    public CompletableFuture<Boolean> rebuild() {
        if (!enabled()) {
            current = null;
            return CompletableFuture.completedFuture(false);
        }
        ensureFolders();
        textureStamp = builder.stamp();
        FileConfiguration config = plugin.getConfig();
        List<PackBuilder.Entry> entries = new ArrayList<>();
        for (CustomItem def : plugin.items().all()) {
            if (def.texture != null) {
                entries.add(new PackBuilder.Entry(def.id, def.texture, parent(def), def.material, def.customModelData));
            }
        }
        PackBuilder.Input input = new PackBuilder.Input(List.copyOf(entries), config.getInt("pack.pack-format", 46),
                config.getInt("pack.min-format", 46), config.getInt("pack.max-format", 999),
                config.getString("pack.description", "RadiantItems"), config.getBoolean("pack.custom-model-data-dispatch", true));
        building = true;
        String previous = current == null ? null : current.sha1Hex();
        Executor async = r -> Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
        Executor main = r -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, r);
            }
        };
        return CompletableFuture.supplyAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                BuiltPack pack = builder.build(input);
                plugin.getLogger().info("Resource pack generado: " + pack.entries() + " archivos, " + (pack.zip().length / 1024)
                        + " KB, SHA-1 " + pack.sha1Hex() + " (" + (System.currentTimeMillis() - start) + "ms)");
                return pack;
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Error generando el resource pack", ex);
                return null;
            }
        }, async).thenApplyAsync(pack -> {
            building = false;
            if (pack == null) {
                return false;
            }
            current = pack;
            return !pack.sha1Hex().equals(previous);
        }, main);
    }

    /** Rebuild and re-send to everyone, but only when the content actually changed. */
    public void rebuildAndSendIfChanged() {
        rebuild().thenAccept(changed -> {
            if (changed) {
                sendAll();
            }
        });
    }

    public boolean texturesChanged() {
        return builder.stamp() != textureStamp;
    }

    public boolean building() {
        return building;
    }

    public BuiltPack current() {
        return current;
    }

    public Map<String, String> report() {
        BuiltPack pack = current;
        return pack == null ? Map.of() : pack.textures();
    }

    public String url(BuiltPack pack) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("http.enabled", true)) {
            String external = config.getString("pack.external-url", "");
            if (external == null || external.isBlank()) {
                return null;
            }
            return external.replace("{sha1}", pack.sha1Hex());
        }
        return baseUrl + "/pack/" + pack.fileName();
    }

    // ───────────────────────── sending ─────────────────────────

    public boolean send(Player player, boolean notifyWhenLoaded) {
        BuiltPack pack = current;
        if (pack == null || !enabled()) {
            return false;
        }
        String url = url(pack);
        if (url == null) {
            plugin.getLogger().warning("http.enabled=false y pack.external-url vacío: no se puede enviar el pack.");
            return false;
        }
        FileConfiguration config = plugin.getConfig();
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), pack.sha1Hex());
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .required(config.getBoolean("pack.required", false))
                .prompt(Text.parse(config.getString("pack.prompt", "")))
                .replace(false)
                .build();
        if (notifyWhenLoaded) {
            notifyOnLoad.add(player.getUniqueId());
        }
        player.sendResourcePacks(request);
        return true;
    }

    public void sendAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!enabled() || !config.getBoolean("pack.send-on-join", true)) {
            return;
        }
        Player player = event.getPlayer();
        long delay = Math.max(1, config.getLong("pack.send-delay-ticks", 40));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                send(player, false);
            }
        }, delay);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        notifyOnLoad.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                if (notifyOnLoad.remove(player.getUniqueId())) {
                    plugin.messages().send(player, "pack-loaded");
                }
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                notifyOnLoad.remove(player.getUniqueId());
                if (event.getStatus() != PlayerResourcePackStatusEvent.Status.DECLINED) {
                    plugin.getLogger().warning("El pack falló para " + player.getName() + ": " + event.getStatus()
                            + " (revisa http.public-url y que el puerto " + plugin.getConfig().getInt("http.port", 8166) + " esté abierto)");
                }
            }
            default -> {
                // ACCEPTED / DOWNLOADED are intermediate states
            }
        }
    }
}
