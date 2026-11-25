package dev.privalo.items.item;

import dev.privalo.items.activator.Activator;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** YAML ⇄ {@link CustomItem}. Pure (no server registries) so it can also run in the offline validator. */
public final class ItemLoader {

    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,48}");
    private static final List<String> FLAG_EXTRAS = List.of("UNBREAKABLE");

    private ItemLoader() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    public static CustomItem read(String id, ConfigurationSection s, List<String> errors) {
        CustomItem def = new CustomItem(id);
        def.material = s.getString("material", "PAPER").trim().toUpperCase(Locale.ROOT);
        if (Material.matchMaterial(def.material) == null) {
            errors.add("material desconocido '" + def.material + "' (se usará PAPER)");
            def.material = "PAPER";
        } else if (!isItem(Material.matchMaterial(def.material))) {
            errors.add("'" + def.material + "' no es un ítem (se usará PAPER)");
            def.material = "PAPER";
        }
        def.name = s.getString("name");
        def.permission = blankToNull(s.getString("permission"));
        def.noPermissionMessage = blankToNull(s.getString("no-permission-message"));
        def.lore.addAll(s.getStringList("lore"));
        int cmd = s.getInt("custom-model-data", 0);
        def.customModelData = cmd > 0 ? cmd : null;
        def.itemModel = blankToNull(s.getString("item-model"));
        def.texture = blankToNull(s.getString("texture"));
        if (def.texture != null) {
            def.texture = def.texture.toLowerCase(Locale.ROOT).replace(".png", "");
            if (!def.texture.matches("[a-z0-9_./-]+")) {
                errors.add("texture '" + def.texture + "' solo admite minúsculas, números, _ - . /");
                def.texture = null;
            }
        }
        def.model = blankToNull(s.getString("model"));
        if (def.model != null && !def.model.equalsIgnoreCase("handheld") && !def.model.equalsIgnoreCase("generated")) {
            errors.add("model debe ser 'handheld' o 'generated', no '" + def.model + "'");
            def.model = null;
        }
        def.glow = s.getBoolean("glow", false);
        ConfigurationSection ench = s.getConfigurationSection("enchantments");
        if (ench != null) {
            for (String key : ench.getKeys(false)) {
                def.enchantments.put(key.toLowerCase(Locale.ROOT), Math.max(1, ench.getInt(key, 1)));
            }
        }
        for (String flag : s.getStringList("flags")) {
            String f = flag.trim().toUpperCase(Locale.ROOT);
            if (FLAG_EXTRAS.contains(f)) {
                def.unbreakable = true;
                continue;
            }
            try {
                ItemFlag.valueOf(f);
                def.flags.add(f);
            } catch (IllegalArgumentException ex) {
                errors.add("flag desconocido '" + flag + "'");
            }
        }
        def.unbreakable = def.unbreakable || s.getBoolean("unbreakable", false);
        for (String attr : s.getStringList("attributes")) {
            try {
                Attributes.parse(attr);
                def.attributes.add(attr);
            } catch (IllegalArgumentException ex) {
                errors.add(ex.getMessage());
            }
        }
        def.color = blankToNull(s.getString("color"));
        def.skullTexture = blankToNull(s.getString("skull-texture"));
        def.maxStack = s.isInt("max-stack") ? Math.max(1, Math.min(99, s.getInt("max-stack"))) : null;

        def.maxUses = Math.max(0, s.getInt("max-uses", 0));
        def.breakOnEmpty = s.getBoolean("break-on-empty", true);
        ConfigurationSection recharge = s.getConfigurationSection("recharge");
        if (recharge != null) {
            def.rechargeMaterial = blankToNull(recharge.getString("material"));
            def.rechargeUses = Math.max(1, recharge.getInt("uses", 1));
            if (def.rechargeMaterial != null && Material.matchMaterial(def.rechargeMaterial) == null) {
                errors.add("recharge.material desconocido '" + def.rechargeMaterial + "'");
                def.rechargeMaterial = null;
            }
        }
        ConfigurationSection vars = s.getConfigurationSection("variables");
        if (vars != null) {
            for (String key : vars.getKeys(false)) {
                def.variables.put(ItemData.cleanVar(key), String.valueOf(vars.get(key)));
            }
        }
        ConfigurationSection r = s.getConfigurationSection("restrictions");
        ConfigurationSection rs = r == null ? s : r;
        def.canCraft = rs.getBoolean("can-craft", false);
        def.canSmelt = rs.getBoolean("can-smelt", false);
        def.canAnvil = rs.getBoolean("can-anvil", false);
        def.canDrop = rs.getBoolean("can-drop", true);
        def.keepOnDeath = rs.getBoolean("keep-on-death", false);
        def.soulbound = rs.getBoolean("soulbound", false);

        ConfigurationSection acts = s.getConfigurationSection("activators");
        if (acts != null) {
            for (String key : acts.getKeys(false)) {
                ConfigurationSection as = acts.getConfigurationSection(key);
                if (as == null) {
                    errors.add("activators." + key + " no es una sección");
                    continue;
                }
                try {
                    Activator a = Activator.read(key, as);
                    a.compile(errors);
                    def.activators.add(a);
                } catch (IllegalArgumentException ex) {
                    errors.add("activador '" + key + "': tipo inválido '" + as.getString("type") + "'");
                }
            }
        }
        def.revision = revision(def);
        return def;
    }

    public static void write(CustomItem def, ConfigurationSection s) {
        s.set("material", def.material);
        if (def.name != null) {
            s.set("name", def.name);
        }
        if (def.permission != null) {
            s.set("permission", def.permission);
        }
        if (def.noPermissionMessage != null) {
            s.set("no-permission-message", def.noPermissionMessage);
        }
        s.set("lore", new ArrayList<>(def.lore));
        if (def.texture != null) {
            s.set("texture", def.texture);
        }
        if (def.model != null) {
            s.set("model", def.model);
        }
        if (def.customModelData != null) {
            s.set("custom-model-data", def.customModelData);
        }
        if (def.itemModel != null) {
            s.set("item-model", def.itemModel);
        }
        s.set("glow", def.glow);
        if (!def.enchantments.isEmpty()) {
            s.createSection("enchantments", new LinkedHashMap<>(def.enchantments));
        }
        s.set("flags", new ArrayList<>(def.flags));
        s.set("unbreakable", def.unbreakable);
        if (!def.attributes.isEmpty()) {
            s.set("attributes", new ArrayList<>(def.attributes));
        }
        if (def.color != null) {
            s.set("color", def.color);
        }
        if (def.skullTexture != null) {
            s.set("skull-texture", def.skullTexture);
        }
        if (def.maxStack != null) {
            s.set("max-stack", def.maxStack);
        }
        s.set("max-uses", def.maxUses);
        if (def.maxUses > 0) {
            s.set("break-on-empty", def.breakOnEmpty);
        }
        if (def.rechargeMaterial != null) {
            ConfigurationSection rc = s.createSection("recharge");
            rc.set("material", def.rechargeMaterial);
            rc.set("uses", def.rechargeUses);
        }
        if (!def.variables.isEmpty()) {
            Map<String, Object> vars = new LinkedHashMap<>();
            def.variables.forEach((k, v) -> vars.put(k, typed(v)));
            s.createSection("variables", vars);
        }
        ConfigurationSection r = s.createSection("restrictions");
        r.set("can-craft", def.canCraft);
        r.set("can-smelt", def.canSmelt);
        r.set("can-anvil", def.canAnvil);
        r.set("can-drop", def.canDrop);
        r.set("keep-on-death", def.keepOnDeath);
        r.set("soulbound", def.soulbound);
        ConfigurationSection acts = s.createSection("activators");
        for (Activator a : def.activators) {
            a.write(acts.createSection(a.key));
        }
    }

    /** Hash of the serialized definition. */
    public static int revision(CustomItem def) {
        YamlConfiguration tmp = new YamlConfiguration();
        write(def, tmp);
        int hash = tmp.saveToString().hashCode();
        return hash == 0 ? 1 : hash;
    }

    private static Object typed(String value) {
        try {
            if (value.matches("-?\\d{1,9}")) {
                return Integer.parseInt(value);
            }
            if (value.matches("-?\\d+\\.\\d+")) {
                return Double.parseDouble(value);
            }
        } catch (NumberFormatException ignored) {
            return value;
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    /** Material#isItem needs the server registries on some versions; offline it is assumed true. */
    private static boolean isItem(Material material) {
        try {
            return material.isItem();
        } catch (Throwable notBootstrapped) {
            return !material.name().endsWith("AIR");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Default definition used by /ri create. */
    public static CustomItem blank(String id) {
        CustomItem def = new CustomItem(id);
        def.material = "PAPER";
        def.name = "<gradient:#ffd36e:#ff8a3d>" + id + "</gradient>";
        def.lore.add("<gray>Ítem creado con el editor de RadiantItems.");
        Activator a = new Activator("click");
        a.type = dev.privalo.items.activator.ActivatorType.RIGHT_CLICK;
        a.cooldown = 1;
        a.actions.add("SOUND: entity.experience_orb.pickup 1 1.2");
        a.actions.add("ACTIONBAR: <gold>¡Has usado %item%!");
        def.activators.add(a);
        def.revision = revision(def);
        return def;
    }
}
