package dev.privalo.items.item;

import dev.privalo.items.activator.Activator;
import dev.privalo.items.activator.ActivatorType;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Definition of a custom item (mutable so the GUI editor can change it and save it back). */
public final class CustomItem {

    public final String id;
    /** File it was loaded from (items/&lt;id&gt;.yml or items.yml). */
    public File source;
    public boolean fromItemsYml;
    /** Sub-folder inside items/ ("" = root, e.g. "pvp" or "pvp/espadas"). */
    public String category = "";

    /** Permission needed to trigger any activator (null = free for everyone). */
    public String permission;
    public String noPermissionMessage;

    public String material = "PAPER";
    public String name;
    public final List<String> lore = new ArrayList<>();
    public Integer customModelData;
    public String itemModel;
    public String texture;
    /** "handheld", "generated" or null = automatic. */
    public String model;
    public boolean glow;
    public final Map<String, Integer> enchantments = new LinkedHashMap<>();
    public final List<String> flags = new ArrayList<>();
    public boolean unbreakable;
    /** "ATTACK_DAMAGE: 8 [slot] [operation]". */
    public final List<String> attributes = new ArrayList<>();
    public String color;
    public String skullTexture;
    public Integer maxStack;

    public int maxUses;
    public boolean breakOnEmpty = true;
    public String rechargeMaterial;
    public int rechargeUses = 1;

    public final Map<String, String> variables = new LinkedHashMap<>();

    public boolean canCraft;
    public boolean canSmelt;
    public boolean canAnvil;
    public boolean canDrop = true;
    public boolean keepOnDeath;
    public boolean soulbound;

    public final List<Activator> activators = new ArrayList<>();

    /** Hash of the serialized definition; stored on stacks so outdated copies get rebuilt. */
    public int revision;

    public CustomItem(String id) {
        this.id = id;
    }

    /** Unique items get a UUID (no stacking) so uses/variables/owner live on each copy. */
    public boolean unique() {
        return maxUses > 0 || !variables.isEmpty() || soulbound || (maxStack != null && maxStack == 1);
    }

    public String displayName() {
        return name == null || name.isBlank() ? "<white>" + id : name;
    }

    public boolean has(ActivatorType type) {
        for (Activator a : activators) {
            if (a.type == type) {
                return true;
            }
        }
        return false;
    }

    public boolean hasLoop() {
        for (Activator a : activators) {
            if (a.type.loop()) {
                return true;
            }
        }
        return false;
    }

    public boolean loreUses(String token) {
        if (name != null && name.contains(token)) {
            return true;
        }
        for (String line : lore) {
            if (line.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public Activator activator(String key) {
        for (Activator a : activators) {
            if (a.key.equals(key)) {
                return a;
            }
        }
        return null;
    }

    public String nextActivatorKey(String base) {
        String clean = base.toLowerCase(java.util.Locale.ROOT);
        if (activator(clean) == null) {
            return clean;
        }
        int i = 2;
        while (activator(clean + "_" + i) != null) {
            i++;
        }
        return clean + "_" + i;
    }
}
