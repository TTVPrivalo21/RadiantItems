package dev.privalo.items.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.privalo.items.RadiantItems;
import dev.privalo.items.util.Expr;
import dev.privalo.items.util.Messages;
import dev.privalo.items.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Builds ItemStacks from definitions and re-renders their dynamic name/lore. */
public final class ItemFactory {

    private static final Map<String, String> ENCHANT_ALIASES = Map.ofEntries(
            Map.entry("damage_all", "sharpness"), Map.entry("durability", "unbreaking"),
            Map.entry("dig_speed", "efficiency"), Map.entry("loot_bonus_blocks", "fortune"),
            Map.entry("loot_bonus_mobs", "looting"), Map.entry("arrow_damage", "power"),
            Map.entry("arrow_fire", "flame"), Map.entry("arrow_infinite", "infinity"),
            Map.entry("arrow_knockback", "punch"), Map.entry("protection_environmental", "protection"),
            Map.entry("protection_fire", "fire_protection"), Map.entry("protection_fall", "feather_falling"),
            Map.entry("protection_explosions", "blast_protection"), Map.entry("protection_projectile", "projectile_protection"),
            Map.entry("oxygen", "respiration"), Map.entry("water_worker", "aqua_affinity"),
            Map.entry("damage_undead", "smite"), Map.entry("damage_arthropods", "bane_of_arthropods"),
            Map.entry("luck", "luck_of_the_sea"));

    private final RadiantItems plugin;

    public ItemFactory(RadiantItems plugin) {
        this.plugin = plugin;
    }

    public static Enchantment enchantment(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        key = ENCHANT_ALIASES.getOrDefault(key, key);
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
    }

    public static Material material(CustomItem def) {
        Material m = Material.matchMaterial(def.material);
        return m == null || m.isAir() ? Material.PAPER : m;
    }

    /** Model id put on the stack: explicit item-model, or radiantitems:&lt;id&gt; when a pack texture exists. */
    public NamespacedKey modelKey(CustomItem def) {
        if (def.itemModel != null) {
            return NamespacedKey.fromString(def.itemModel.toLowerCase(Locale.ROOT));
        }
        FileConfiguration config = plugin.getConfig();
        if (def.texture != null && config.getBoolean("resource-pack.enabled", false) && config.getBoolean("pack.use-item-model", true)) {
            return new NamespacedKey(ItemData.NS, def.id);
        }
        return null;
    }

    /** Stored revision: also changes when the resource pack is toggled, so item_model is added/removed. */
    public int revision(CustomItem def) {
        return modelKey(def) == null ? def.revision : def.revision ^ 0x5F3759DF;
    }

    public ItemStack create(CustomItem def, int amount) {
        ItemStack stack = new ItemStack(material(def), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        applyStatic(def, meta);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        ItemData.stamp(pdc, def.id, def.unique() ? UUID.randomUUID() : null, revision(def));
        if (def.maxUses > 0) {
            ItemData.setUses(pdc, def.maxUses);
        }
        if (!def.variables.isEmpty()) {
            ItemData.vars(pdc, def.variables);
        }
        render(def, meta, null);
        stack.setItemMeta(meta);
        return stack;
    }

    /** New stack with the current definition, keeping UUID, uses, variables, owner and durability. */
    public ItemStack rebuild(ItemStack old, CustomItem def) {
        ItemMeta oldMeta = old.getItemMeta();
        PersistentDataContainer oldPdc = oldMeta.getPersistentDataContainer();
        ItemStack fresh = new ItemStack(material(def), old.getAmount());
        ItemMeta meta = fresh.getItemMeta();
        applyStatic(def, meta);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        UUID uuid = ItemData.uuid(oldPdc);
        if (def.unique() && uuid == null) {
            uuid = UUID.randomUUID();
        } else if (!def.unique()) {
            uuid = null;
        }
        ItemData.stamp(pdc, def.id, uuid, revision(def));
        if (def.maxUses > 0) {
            ItemData.setUses(pdc, Math.min(def.maxUses, ItemData.uses(oldPdc, def.maxUses)));
        }
        Map<String, String> vars = new java.util.LinkedHashMap<>(def.variables);
        vars.putAll(ItemData.vars(oldPdc));
        if (!vars.isEmpty()) {
            ItemData.vars(pdc, vars);
        }
        UUID owner = ItemData.owner(oldPdc);
        if (owner != null) {
            ItemData.owner(pdc, owner, ItemData.ownerName(oldPdc));
        }
        if (oldMeta instanceof Damageable od && meta instanceof Damageable nd && od.hasDamage()) {
            nd.setDamage(od.getDamage());
        }
        render(def, meta, null);
        fresh.setItemMeta(meta);
        return fresh;
    }

    private void applyStatic(CustomItem def, ItemMeta meta) {
        if (def.customModelData != null) {
            meta.setCustomModelData(def.customModelData);
        }
        NamespacedKey model = modelKey(def);
        if (model != null) {
            meta.setItemModel(model);
        }
        if (def.glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        def.enchantments.forEach((name, level) -> {
            Enchantment enchantment = enchantment(name);
            if (enchantment == null) {
                plugin.getLogger().warning("[" + def.id + "] encantamiento desconocido: " + name);
            } else {
                meta.addEnchant(enchantment, level, true);
            }
        });
        for (String flag : def.flags) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flag));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[" + def.id + "] flag desconocido: " + flag);
            }
        }
        if (def.unbreakable) {
            meta.setUnbreakable(true);
        }
        int index = 0;
        for (String line : def.attributes) {
            Attributes.Spec spec = Attributes.parse(line);
            Attribute attribute = Attributes.resolve(spec.attribute());
            if (attribute == null) {
                plugin.getLogger().warning("[" + def.id + "] atributo desconocido: " + spec.attribute());
                continue;
            }
            NamespacedKey key = new NamespacedKey(ItemData.NS, "attr_" + def.id.replace('-', '_') + "_" + index++);
            meta.addAttributeModifier(attribute, new AttributeModifier(key, spec.amount(), spec.operation(), Attributes.slot(spec.slot())));
        }
        if (def.color != null) {
            Color color = Text.color(def.color, Color.WHITE);
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(color);
            } else if (meta instanceof PotionMeta potion) {
                potion.setColor(color);
            }
        }
        if (def.skullTexture != null && meta instanceof SkullMeta skull) {
            UUID id = UUID.nameUUIDFromBytes(def.skullTexture.getBytes(StandardCharsets.UTF_8));
            PlayerProfile profile = Bukkit.createProfile(id, null);
            profile.setProperty(new ProfileProperty("textures", def.skullTexture));
            skull.setPlayerProfile(profile);
        }
        if (def.unique()) {
            meta.setMaxStackSize(1);
        } else if (def.maxStack != null) {
            meta.setMaxStackSize(def.maxStack);
        }
    }

    /** Re-renders name and lore (uses, cooldown, variables...). */
    public void render(CustomItem def, ItemMeta meta, Player holder) {
        meta.displayName(Text.item(placeholders(def, meta, holder, def.displayName())));
        List<Component> lore = new ArrayList<>(def.lore.size());
        for (String line : def.lore) {
            lore.add(Text.item(placeholders(def, meta, holder, line)));
        }
        meta.lore(lore);
    }

    /** Re-renders a stack in place; returns false if it isn't a known custom item. */
    public boolean refresh(ItemStack stack, Player holder) {
        String id = ItemData.id(stack);
        CustomItem def = id == null ? null : plugin.items().get(id);
        if (def == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        render(def, meta, holder);
        stack.setItemMeta(meta);
        return true;
    }

    public String usesText(CustomItem def, ItemMeta meta) {
        if (def.maxUses <= 0) {
            return plugin.getConfig().getString("format.unlimited-uses", "∞");
        }
        return String.valueOf(ItemData.uses(meta.getPersistentDataContainer(), def.maxUses));
    }

    public String placeholders(CustomItem def, ItemMeta meta, Player holder, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String out = text
                .replace("%uses%", usesText(def, meta))
                .replace("%max_uses%", def.maxUses <= 0 ? plugin.getConfig().getString("format.unlimited-uses", "∞") : String.valueOf(def.maxUses))
                .replace("%id%", def.id);
        if (out.contains("%owner%")) {
            String owner = ItemData.ownerName(pdc);
            out = out.replace("%owner%", owner == null ? plugin.getConfig().getString("format.no-owner", "-") : owner);
        }
        if (out.contains("%cooldown%")) {
            double left = plugin.cooldowns().remaining(def, holder, ItemData.uuid(pdc));
            out = out.replace("%cooldown%", left <= 0
                    ? plugin.getConfig().getString("format.cooldown-ready", "Listo")
                    : Messages.apply(plugin.getConfig().getString("format.cooldown", "%time%s"), "time", String.format(Locale.ROOT, "%.1f", left)));
        }
        if (out.contains("%player%")) {
            out = out.replace("%player%", holder == null ? "" : holder.getName());
        }
        if (out.indexOf('%') >= 0) {
            for (Map.Entry<String, String> e : ItemData.effectiveVars(def, meta).entrySet()) {
                String value = e.getValue();
                if (Expr.isNumeric(value)) {
                    value = Expr.format(Expr.eval(value));
                }
                out = out.replace("%" + e.getKey() + "%", value).replace("%var_" + e.getKey() + "%", value);
            }
        }
        return out;
    }
}
