package dev.privalo.items;

import dev.privalo.items.action.ActionRunner;
import dev.privalo.items.activator.ActivatorEngine;
import dev.privalo.items.activator.Cooldowns;
import dev.privalo.items.activator.LoopTask;
import dev.privalo.items.activator.TriggerListener;
import dev.privalo.items.command.ItemsCommand;
import dev.privalo.items.gui.ChatInput;
import dev.privalo.items.gui.Menu;
import dev.privalo.items.gui.MenuListener;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.item.ItemFactory;
import dev.privalo.items.item.ItemRegistry;
import dev.privalo.items.listener.Guards;
import dev.privalo.items.listener.ItemListener;
import dev.privalo.items.pack.PackService;
import dev.privalo.items.util.Hooks;
import dev.privalo.items.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.List;

/** RadiantItems: executable custom items for Paper 1.21.x – 26.x. */
public final class RadiantItems extends JavaPlugin {

    private final Messages messages = new Messages();
    private final Guards guards = new Guards();
    private final Cooldowns cooldowns = new Cooldowns();
    private Hooks hooks;
    private ItemRegistry items;
    private ItemFactory factory;
    private ActivatorEngine engine;
    private ActionRunner actions;
    private TriggerListener triggers;
    private LoopTask loop;
    private ChatInput chat;
    private PackService pack;
    private BukkitTask watcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        copyDefaults();
        loadSecret();
        messages.load(getConfig());
        hooks = new Hooks(getLogger());
        items = new ItemRegistry(this);
        factory = new ItemFactory(this);
        engine = new ActivatorEngine(this);
        actions = new ActionRunner(this);
        triggers = new TriggerListener(this);
        loop = new LoopTask(this);
        chat = new ChatInput(this);
        pack = new PackService(this);

        int count = items.loadAll();
        getLogger().info(count + " ítems cargados.");

        var pm = getServer().getPluginManager();
        pm.registerEvents(triggers, this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(chat, this);
        pm.registerEvents(pack, this);

        PluginCommand command = getCommand("radiantitems");
        if (command != null) {
            ItemsCommand executor = new ItemsCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // soft dependencies may enable after us
        Bukkit.getScheduler().runTask(this, hooks::load);
        loop.start();
        pack.startHttp();
        if (pack.enabled()) {
            pack.rebuild();
        } else {
            getLogger().info("Modo sin texturas: resource-pack.enabled=false (sin servidor HTTP ni pack). Todo funciona con ítems vanilla.");
        }
        startWatcher();
    }

    @Override
    public void onDisable() {
        if (watcher != null) {
            watcher.cancel();
        }
        if (loop != null) {
            loop.stop();
        }
        if (pack != null) {
            pack.stop();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                player.closeInventory();
            }
        }
    }

    /** /ri reload: config, messages, hooks, items, loop, HTTP and pack. Returns the item count. */
    public int reload() {
        reloadConfig();
        messages.load(getConfig());
        hooks.load();
        int count = items.loadAll();
        loop.start();
        pack.startHttp();
        pack.rebuildAndSendIfChanged();
        startWatcher();
        Bukkit.getOnlinePlayers().forEach(loop::updateOutdated);
        return count;
    }

    /** After the editor saves an item: refresh online copies and the pack (resent only if it changed). */
    public void onItemEdited(CustomItem def) {
        Bukkit.getOnlinePlayers().forEach(loop::updateOutdated);
        if (pack.enabled() && (def.texture != null || pack.current() != null)) {
            pack.rebuildAndSendIfChanged();
        }
    }

    private void startWatcher() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
        if (!getConfig().getBoolean("auto-reload.enabled", true)) {
            return;
        }
        long period = Math.max(1, getConfig().getLong("auto-reload.interval-seconds", 3)) * 20L;
        watcher = Bukkit.getScheduler().runTaskTimer(this, () -> {
            boolean itemsChanged = items.changedOnDisk();
            if (itemsChanged) {
                int count = items.loadAll();
                getLogger().info("Cambios detectados en items/: " + count + " ítems recargados.");
                Bukkit.getOnlinePlayers().forEach(loop::updateOutdated);
            }
            if (pack.enabled() && (itemsChanged || pack.texturesChanged()) && !pack.building()) {
                pack.rebuildAndSendIfChanged();
            }
        }, period, period);
    }

    /**
     * Extracts every bundled items/(sub-folders)/*.yml when items.generate-defaults is true
     * and items/ has no YAML yet. items.yml is only written when missing.
     */
    private void copyDefaults() {
        if (!new File(getDataFolder(), "items.yml").isFile()) {
            saveResource("items.yml", false);
        }
        if (!getConfig().getBoolean("items.generate-defaults", true)) {
            return;
        }
        File itemsDir = new File(getDataFolder(), "items");
        if (itemsDir.isDirectory() && hasYaml(itemsDir.toPath())) {
            return;
        }
        int extracted = 0;
        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("items/") && name.endsWith(".yml")) {
                    saveResource(name, false);
                    extracted++;
                }
            }
        } catch (IOException ex) {
            getLogger().severe("No se pudieron extraer los ítems por defecto: " + ex.getMessage());
        }
        getLogger().info(extracted + " ítems por defecto extraídos en items/ (items.generate-defaults: true).");
    }

    private static boolean hasYaml(java.nio.file.Path dir) {
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(dir)) {
            return walk.anyMatch(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".yml"));
        } catch (IOException ex) {
            return true;
        }
    }

    /** Per-server HMAC secret for item signatures (plugins/RadiantItems/.secret). */
    private void loadSecret() {
        File file = new File(getDataFolder(), ".secret");
        try {
            if (file.isFile()) {
                byte[] decoded = Base64.getDecoder().decode(Files.readString(file.toPath(), StandardCharsets.UTF_8).trim());
                if (decoded.length >= 16) {
                    ItemData.secret(decoded);
                    return;
                }
            }
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(file.toPath(), Base64.getEncoder().encodeToString(secret), StandardCharsets.UTF_8);
            ItemData.secret(secret);
            getLogger().info("Generada una clave nueva para firmar ítems (.secret). No la borres o los ítems existentes dejarán de funcionar.");
        } catch (IOException | IllegalArgumentException ex) {
            getLogger().severe("No se pudo leer/crear .secret (" + ex.getMessage() + "): se usa una clave temporal.");
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            ItemData.secret(secret);
        }
    }

    /** Splits an amount into stacks: unique items one by one, others by stack size. */
    public List<ItemStack> giveStacks(CustomItem def, int amount) {
        List<ItemStack> out = new ArrayList<>();
        if (def.unique()) {
            for (int i = 0; i < amount; i++) {
                out.add(factory.create(def, 1));
            }
            return out;
        }
        Material material = ItemFactory.material(def);
        int stackSize = def.maxStack != null ? def.maxStack : material.getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, Math.max(1, stackSize));
            out.add(factory.create(def, n));
            left -= n;
        }
        return out;
    }

    public Messages messages() {
        return messages;
    }

    public Guards guards() {
        return guards;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public Hooks hooks() {
        return hooks;
    }

    public ItemRegistry items() {
        return items;
    }

    public ItemFactory factory() {
        return factory;
    }

    public ActivatorEngine engine() {
        return engine;
    }

    public ActionRunner actions() {
        return actions;
    }

    public TriggerListener triggers() {
        return triggers;
    }

    public LoopTask loop() {
        return loop;
    }

    public ChatInput chat() {
        return chat;
    }

    public PackService pack() {
        return pack;
    }
}
