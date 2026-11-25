import dev.privalo.items.action.Action;
import dev.privalo.items.action.ActionRunner;
import dev.privalo.items.activator.Activator;
import dev.privalo.items.condition.Condition;
import dev.privalo.items.item.Attributes;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemLoader;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Strict offline validation of config.yml, plugin.yml, items.yml and items/**.yml. */
public class ValidateYaml {

    static final Set<String> EFFECTS = Set.of("speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health",
            "instant_damage", "jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing",
            "invisibility", "blindness", "night_vision", "hunger", "weakness", "poison", "wither", "health_boost",
            "absorption", "saturation", "glowing", "levitation", "luck", "unluck", "slow_falling", "conduit_power",
            "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness", "trial_omen", "raid_omen", "wind_charged",
            "weaving", "oozing", "infested");
    static final Set<String> PROJECTILES = Set.of("FIREBALL", "METEOR", "SMALL_FIREBALL", "ARROW", "SPECTRAL_ARROW",
            "SNOWBALL", "EGG", "TRIDENT", "WITHER_SKULL", "DRAGON_FIREBALL", "WIND_CHARGE", "ENDER_PEARL", "PEARL");
    /** Particles whose data type changed across 1.21.x – 26.x: refused so the catalogue works everywhere. */
    static final Set<String> UNSTABLE_PARTICLES = Set.of("EFFECT", "INSTANT_EFFECT", "ENTITY_EFFECT", "DRAGON_BREATH");

    static List<String> errors = new ArrayList<>();
    static Map<String, String> idToFile = new TreeMap<>();
    static List<CustomItem> defs = new ArrayList<>();
    static Map<String, Integer> perFolder = new TreeMap<>();

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        for (String name : new String[]{"config.yml", "plugin.yml"}) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(new File(res, name));
            System.out.println("OK  " + name + " (" + y.getKeys(true).size() + " claves)");
        }
        YamlConfiguration itemsYml = new YamlConfiguration();
        itemsYml.load(new File(res, "items.yml"));
        ConfigurationSection sec = itemsYml.getConfigurationSection("items");
        for (String id : sec.getKeys(false)) {
            load("items.yml#" + id, id, sec.getConfigurationSection(id), "items.yml");
        }
        Path base = new File(res, "items").toPath();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(base)) {
            files = walk.filter(p -> p.toString().endsWith(".yml")).sorted().toList();
        }
        for (Path p : files) {
            String rel = base.relativize(p).toString().replace('\\', '/');
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.load(p.toFile());
            } catch (Exception ex) {
                errors.add(rel + ": YAML inválido: " + ex.getMessage());
                continue;
            }
            String fileId = p.getFileName().toString().replace(".yml", "");
            String id = y.getString("id", fileId);
            if (!id.equals(fileId)) {
                errors.add(rel + ": el id '" + id + "' no coincide con el nombre del archivo");
            }
            String folder = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "(raíz)";
            perFolder.merge(folder, 1, Integer::sum);
            load(rel, id, y, rel);
        }
        // reference checks need every id loaded first
        for (CustomItem def : defs) {
            checkReferences(def);
        }
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.load(new File(res, "config.yml"));
        for (String key : args[1].split(",")) {
            if (!key.isBlank() && !cfg.isSet("messages." + key)) {
                errors.add("config.yml: falta messages." + key);
            }
        }
        for (Condition.Type t : Condition.Type.values()) {
            if (!cfg.isSet("messages.conditions." + t.name())) {
                errors.add("config.yml: falta messages.conditions." + t.name());
            }
        }
        if (cfg.getBoolean("resource-pack.enabled", true)) {
            errors.add("config.yml: resource-pack.enabled debe ser false por defecto");
        }
        String source = cfg.getString("update-checker.source", "");
        if (!cfg.isConfigurationSection("update-checker")) {
            errors.add("config.yml: falta la sección update-checker");
        } else if (!source.equals("github") && !source.equals("spigot")) {
            errors.add("config.yml: update-checker.source debe ser \"github\" o \"spigot\", no '" + source + "'");
        } else if (!cfg.getString("update-checker.github-repo", "").matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            errors.add("config.yml: update-checker.github-repo debe tener el formato usuario/repositorio");
        } else if (!cfg.isInt("update-checker.spigot-resource-id") || cfg.getInt("update-checker.spigot-resource-id") < 0) {
            errors.add("config.yml: update-checker.spigot-resource-id debe ser un entero >= 0");
        }
        if (!cfg.isSet("items.generate-defaults")) {
            errors.add("config.yml: falta items.generate-defaults");
        }
        perFolder.forEach((folder, count) -> System.out.println("    carpeta " + folder + ": " + count + " ítems"));
        System.out.println("    total: " + defs.size() + " ítems (" + files.size() + " archivos en items/ + items.yml), IDs únicos: " + idToFile.size());
        errors.forEach(e -> System.out.println("ERR " + e));
        System.out.println(errors.isEmpty() ? "VALIDACION OK" : "VALIDACION CON " + errors.size() + " ERRORES");
        System.exit(errors.isEmpty() ? 0 : 1);
    }

    static void load(String label, String id, ConfigurationSection s, String file) {
        String previous = idToFile.putIfAbsent(id, file);
        if (previous != null) {
            errors.add(label + ": id duplicado '" + id + "' (ya en " + previous + ")");
            return;
        }
        List<String> errs = new ArrayList<>();
        CustomItem def = ItemLoader.read(id, s, errs);
        errs.forEach(e -> errors.add(label + ": " + e));
        if (def.name == null || def.name.isBlank()) {
            errors.add(label + ": falta name");
        }
        if (def.lore.isEmpty()) {
            errors.add(label + ": falta lore");
        }
        for (String key : s.getKeys(false)) {
            if (!Set.of("id", "material", "name", "lore", "texture", "model", "custom-model-data", "item-model", "glow",
                    "enchantments", "flags", "unbreakable", "attributes", "color", "skull-texture", "max-stack", "max-uses",
                    "break-on-empty", "recharge", "variables", "restrictions", "activators", "permission",
                    "no-permission-message").contains(key)) {
                errors.add(label + ": clave desconocida '" + key + "'");
            }
        }
        ConfigurationSection restrictions = s.getConfigurationSection("restrictions");
        if (restrictions != null) {
            for (String k : restrictions.getKeys(false)) {
                if (!Set.of("can-craft", "can-smelt", "can-anvil", "can-drop", "can-store", "keep-on-death", "soulbound",
                        "no-craft", "no-smelt", "no-anvil", "no-drop", "no-store").contains(k)) {
                    errors.add(label + ": restricción desconocida '" + k + "'");
                }
            }
        }
        ConfigurationSection acts = s.getConfigurationSection("activators");
        if (acts != null) {
            for (String key : acts.getKeys(false)) {
                ConfigurationSection a = acts.getConfigurationSection(key);
                for (String k : a.getKeys(false)) {
                    if (!Set.of("type", "click", "slot", "interval", "cooldown", "cooldown-scope", "cooldown-message", "chance",
                            "consume-usage", "consume-item", "cancel-event", "entities", "conditions", "actions", "messages").contains(k)) {
                        errors.add(label + ": activador '" + key + "' clave desconocida '" + k + "'");
                    }
                }
            }
        }
        for (String attr : def.attributes) {
            String name = Attributes.parse(attr).attribute().toLowerCase(Locale.ROOT).replace("generic_", "");
            if (!Attributes.COMMON.contains(name)) {
                errors.add(label + ": atributo no reconocido '" + name + "'");
            }
        }
        for (String ench : def.enchantments.keySet()) {
            if (!ench.matches("[a-z_]+")) {
                errors.add(label + ": encantamiento inválido '" + ench + "'");
            }
        }
        if (def.skullTexture != null) {
            try {
                String json = new String(java.util.Base64.getDecoder().decode(def.skullTexture));
                if (!json.contains("textures.minecraft.net/texture/")) {
                    errors.add(label + ": skull-texture no contiene una URL de textures.minecraft.net");
                }
            } catch (IllegalArgumentException ex) {
                errors.add(label + ": skull-texture no es base64 válido");
            }
        }
        def.source = new File(label);
        defs.add(def);
    }

    static void checkReferences(CustomItem def) {
        String label = def.source.getPath();
        for (Activator a : def.activators) {
            for (Condition c : a.parsedConditions()) {
                if (c.type() == Condition.Type.ITEM) {
                    material(label, c.value().trim().split("\\s+")[0]);
                }
            }
            for (Action action : a.parsedActions()) {
                checkAction(label + " [" + a.key + "]", action);
            }
        }
    }

    static void checkAction(String label, Action action) {
        String[] p = action.args().trim().isEmpty() ? new String[0] : action.args().trim().split("\\s+");
        switch (action.type()) {
            case PARTICLE, PARTICLE_CIRCLE, PARTICLE_LINE, PARTICLE_SPIRAL -> {
                String name = ActionRunner.particleName(p[0]);
                if (UNSTABLE_PARTICLES.contains(name)) {
                    errors.add(label + ": partícula " + name + " cambia de tipo entre versiones; usa otra");
                } else {
                    try {
                        Particle.valueOf(name);
                    } catch (IllegalArgumentException ex) {
                        errors.add(label + ": partícula desconocida " + p[0]);
                    }
                }
            }
            case EFFECT -> effect(label, p[0]);
            case REMOVE_EFFECT -> {
                if (!p[0].equalsIgnoreCase("ALL")) {
                    effect(label, p[0]);
                }
            }
            case SOUND -> {
                if (!p[0].matches("[a-z0-9_.:]+") && !p[0].matches("[A-Z0-9_]+")) {
                    errors.add(label + ": clave de sonido inválida " + p[0]);
                }
            }
            case PROJECTILE -> {
                if (!PROJECTILES.contains(p[0].toUpperCase(Locale.ROOT))) {
                    errors.add(label + ": proyectil desconocido " + p[0]);
                }
            }
            case LAUNCH_ENTITY -> {
                try {
                    EntityType type = EntityType.valueOf(p[0].toUpperCase(Locale.ROOT));
                    if (type == EntityType.PLAYER) {
                        errors.add(label + ": no se puede lanzar PLAYER");
                    }
                } catch (IllegalArgumentException ex) {
                    errors.add(label + ": entidad desconocida " + p[0]);
                }
            }
            case FIREWORK -> {
                try {
                    FireworkEffect.Type.valueOf(p[0].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    errors.add(label + ": tipo de fuego artificial desconocido " + p[0]);
                }
            }
            case GIVE_ITEM, TAKE_ITEM -> material(label, p[0]);
            case LOOT -> {
                for (Action.LootEntry entry : action.loot()) {
                    for (Action sub : entry.actions()) {
                        checkAction(label + " LOOT", sub);
                    }
                }
            }
            default -> {
                // validated by Action.parse already
            }
        }
    }

    static void effect(String label, String raw) {
        if (!EFFECTS.contains(ActionRunner.effectKey(raw))) {
            errors.add(label + ": efecto desconocido " + raw);
        }
    }

    static void material(String label, String raw) {
        if (raw.toLowerCase(Locale.ROOT).startsWith("ri:")) {
            if (!idToFile.containsKey(raw.substring(3).toLowerCase(Locale.ROOT))) {
                errors.add(label + ": referencia a ítem inexistente " + raw);
            }
        } else if (Material.matchMaterial(raw) == null) {
            errors.add(label + ": material desconocido " + raw);
        }
    }
}
