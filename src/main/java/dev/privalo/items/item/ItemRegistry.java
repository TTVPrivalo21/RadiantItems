package dev.privalo.items.item;

import dev.privalo.items.RadiantItems;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Loads every *.yml under items/ (recursively, any depth, no item limit) + items.yml, keeps them by id
 * and saves editor changes back to the file/sub-folder they came from.
 */
public final class ItemRegistry {

    private final RadiantItems plugin;
    private final File itemsDir;
    private final File itemsYml;
    private Map<String, CustomItem> items = Collections.emptyMap();
    private long stamp;

    public ItemRegistry(RadiantItems plugin) {
        this.plugin = plugin;
        this.itemsDir = new File(plugin.getDataFolder(), "items");
        this.itemsYml = new File(plugin.getDataFolder(), "items.yml");
    }

    public File folder() {
        return itemsDir;
    }

    /** Every *.yml below items/, sorted by relative path; folders starting with '.' (e.g. .trash) are skipped. */
    public List<File> itemFiles() {
        List<File> out = new ArrayList<>();
        if (!itemsDir.isDirectory()) {
            return out;
        }
        Path base = itemsDir.toPath();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .filter(p -> {
                        for (Path part : base.relativize(p)) {
                            if (part.toString().startsWith(".")) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .forEach(p -> out.add(p.toFile()));
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo recorrer items/: " + ex.getMessage());
        }
        return out;
    }

    /** "pvp/espadas" for items/pvp/espadas/x.yml, "" for items/x.yml. */
    public String categoryOf(File file) {
        Path parent = itemsDir.toPath().relativize(file.toPath()).getParent();
        return parent == null ? "" : parent.toString().replace('\\', '/');
    }

    public int loadAll() {
        Map<String, CustomItem> loaded = new TreeMap<>();
        if (itemsYml.isFile()) {
            YamlConfiguration yml = load(itemsYml);
            ConfigurationSection section = yml == null ? null : yml.getConfigurationSection("items");
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(id);
                    if (s != null) {
                        register(loaded, id, s, itemsYml, true, "");
                    }
                }
            }
        }
        for (File file : itemFiles()) {
            YamlConfiguration yml = load(file);
            if (yml == null) {
                continue;
            }
            String fallback = file.getName().substring(0, file.getName().length() - 4);
            register(loaded, yml.getString("id", fallback), yml, file, false, categoryOf(file));
        }
        items = loaded;
        stamp = computeStamp();
        return loaded.size();
    }

    private void register(Map<String, CustomItem> into, String rawId, ConfigurationSection s, File source, boolean fromItemsYml, String category) {
        String id = rawId.toLowerCase(Locale.ROOT);
        if (!ItemLoader.ID.matcher(id).matches()) {
            plugin.getLogger().warning("Id de ítem inválido '" + rawId + "' en " + source.getName() + " (solo a-z 0-9 _ -)");
            return;
        }
        CustomItem existing = into.get(id);
        if (existing != null) {
            plugin.getLogger().warning("Ítem duplicado '" + id + "' en " + source.getPath() + " (ya definido en "
                    + existing.source.getPath() + "): se ignora esta copia.");
            return;
        }
        List<String> errors = new ArrayList<>();
        CustomItem def = ItemLoader.read(id, s, errors);
        def.source = source;
        def.fromItemsYml = fromItemsYml;
        def.category = category;
        for (String error : errors) {
            plugin.getLogger().warning("[" + id + "] " + error);
        }
        into.put(id, def);
    }

    private YamlConfiguration load(File file) {
        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(file);
            return yml;
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("YAML inválido en " + file.getPath() + ": " + ex.getMessage());
            return null;
        }
    }

    public CustomItem get(String id) {
        return id == null ? null : items.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomItem> all() {
        return items.values();
    }

    public List<String> ids() {
        return new ArrayList<>(items.keySet());
    }

    /** Every category in use (with parents: "pvp/espadas" also adds "pvp"). */
    public List<String> categories() {
        TreeSet<String> out = new TreeSet<>();
        for (CustomItem def : items.values()) {
            String c = def.category;
            while (c != null && !c.isEmpty()) {
                out.add(c);
                int slash = c.lastIndexOf('/');
                c = slash < 0 ? "" : c.substring(0, slash);
            }
        }
        return new ArrayList<>(out);
    }

    /** Items whose category is {@code filter} or below it ("" / null = all). */
    public List<CustomItem> inCategory(String filter) {
        List<CustomItem> out = new ArrayList<>();
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT).replace('\\', '/');
        for (CustomItem def : items.values()) {
            String c = def.category.toLowerCase(Locale.ROOT);
            if (f.isEmpty() || c.equals(f) || c.startsWith(f + "/")) {
                out.add(def);
            }
        }
        return out;
    }

    public boolean exists(String id) {
        return items.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /** Adds a brand-new item (from /ri create) and saves it. */
    public void add(CustomItem def) throws IOException {
        Map<String, CustomItem> copy = new TreeMap<>(items);
        copy.put(def.id, def);
        items = copy;
        save(def);
    }

    /** Normalises a category typed by a user ("PvP\\Espadas/" → "pvp/espadas"). */
    public static String cleanCategory(String raw) {
        if (raw == null) {
            return "";
        }
        String c = raw.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        List<String> parts = new ArrayList<>();
        for (String part : c.split("/")) {
            String clean = part.replaceAll("[^a-z0-9_-]", "_");
            if (!clean.isEmpty() && !clean.startsWith(".") && !clean.equals("_")) {
                parts.add(clean);
            }
        }
        return String.join("/", parts);
    }

    /**
     * Writes the item to its own file: the file it was loaded from when it still sits in the right
     * category folder, otherwise items/&lt;category&gt;/&lt;id&gt;.yml (the old file is removed after a move).
     * Items that came from items.yml are moved out of it.
     */
    public void save(CustomItem def) throws IOException {
        def.revision = ItemLoader.revision(def);
        File dir = def.category.isEmpty() ? itemsDir : new File(itemsDir, def.category);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("No se pudo crear " + dir);
        }
        File old = def.fromItemsYml ? null : def.source;
        File target = old != null && old.isFile() && dir.getCanonicalFile().equals(old.getParentFile().getCanonicalFile())
                ? old : new File(dir, def.id + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(List.of("RadiantItems · " + def.id, "Guardado por el editor in-game (/ri edit " + def.id + ")"));
        yml.set("id", def.id);
        ItemLoader.write(def, yml);
        yml.save(target);
        if (old != null && old.isFile() && !old.getCanonicalFile().equals(target.getCanonicalFile())) {
            Files.delete(old.toPath());
        }
        if (def.fromItemsYml && itemsYml.isFile()) {
            YamlConfiguration legacy = load(itemsYml);
            if (legacy != null) {
                legacy.set("items." + def.id, null);
                legacy.save(itemsYml);
            }
        }
        def.source = target;
        def.fromItemsYml = false;
        stamp = computeStamp();
    }

    /** Moves the item file to items/.trash/ (recoverable) and unregisters it. */
    public boolean delete(String id) throws IOException {
        CustomItem def = get(id);
        if (def == null) {
            return false;
        }
        if (def.fromItemsYml) {
            YamlConfiguration legacy = load(itemsYml);
            if (legacy != null) {
                legacy.set("items." + def.id, null);
                legacy.save(itemsYml);
            }
        } else if (def.source != null && def.source.isFile()) {
            File trash = new File(itemsDir, ".trash");
            if (!trash.isDirectory() && !trash.mkdirs()) {
                throw new IOException("No se pudo crear " + trash);
            }
            Files.move(def.source.toPath(), new File(trash, def.id + "-" + System.currentTimeMillis() + ".yml").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Map<String, CustomItem> copy = new TreeMap<>(items);
        copy.remove(def.id);
        items = copy;
        stamp = computeStamp();
        return true;
    }

    /** Changes when any item file is added, removed or modified anywhere below items/ (auto-reload). */
    public long computeStamp() {
        long value = itemsYml.isFile() ? itemsYml.lastModified() * 31 + itemsYml.length() : 7;
        for (File f : itemFiles()) {
            value = value * 31 + f.getPath().hashCode();
            value = value * 31 + f.lastModified();
            value = value * 31 + f.length();
        }
        return value;
    }

    public boolean changedOnDisk() {
        return computeStamp() != stamp;
    }
}
