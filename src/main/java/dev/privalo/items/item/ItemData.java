package dev.privalo.items.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Everything RadiantItems stores on an ItemStack's PersistentDataContainer.
 * The id is HMAC-signed with a per-server secret so hand-made NBT can't impersonate a custom item.
 */
public final class ItemData {

    public static final String NS = "radiantitems";
    public static final NamespacedKey ID = new NamespacedKey(NS, "id");
    public static final NamespacedKey SIG = new NamespacedKey(NS, "sig");
    public static final NamespacedKey UUID_KEY = new NamespacedKey(NS, "uuid");
    public static final NamespacedKey USES = new NamespacedKey(NS, "uses");
    public static final NamespacedKey VARS = new NamespacedKey(NS, "vars");
    public static final NamespacedKey OWNER = new NamespacedKey(NS, "owner");
    public static final NamespacedKey OWNER_NAME = new NamespacedKey(NS, "owner_name");
    public static final NamespacedKey REV = new NamespacedKey(NS, "rev");
    /** On projectiles: item id / item uuid / "1" when explosions must not break blocks. */
    public static final NamespacedKey PROJECTILE_ITEM = new NamespacedKey(NS, "projectile_item");
    public static final NamespacedKey PROJECTILE_UUID = new NamespacedKey(NS, "projectile_uuid");
    public static final NamespacedKey SAFE = new NamespacedKey(NS, "safe");
    /** Entities spawned by LAUNCH_ENTITY: no drops, no XP. */
    public static final NamespacedKey TEMPORARY = new NamespacedKey(NS, "temporary");

    private static volatile byte[] secret = new byte[32];

    private ItemData() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    public static void secret(byte[] value) {
        secret = value.clone();
    }

    static String sign(String id, String uuid) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal((id + "|" + (uuid == null ? "" : uuid)).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out, 0, 16);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 no disponible", ex);
        }
    }

    /** Verified id, or null for vanilla/forged items. */
    public static String id(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return id(stack.getItemMeta().getPersistentDataContainer());
    }

    public static String id(PersistentDataContainer pdc) {
        String id = pdc.get(ID, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        String sig = pdc.get(SIG, PersistentDataType.STRING);
        String uuid = pdc.get(UUID_KEY, PersistentDataType.STRING);
        if (sig == null || !MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8), sign(id, uuid).getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return id;
    }

    /** True if the stack carries an id key but the signature does not match. */
    public static boolean forged(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(ID, PersistentDataType.STRING) && id(pdc) == null;
    }

    public static void stamp(PersistentDataContainer pdc, String id, UUID uuid, int revision) {
        String u = uuid == null ? null : uuid.toString();
        pdc.set(ID, PersistentDataType.STRING, id);
        if (u != null) {
            pdc.set(UUID_KEY, PersistentDataType.STRING, u);
        } else {
            pdc.remove(UUID_KEY);
        }
        pdc.set(SIG, PersistentDataType.STRING, sign(id, u));
        pdc.set(REV, PersistentDataType.INTEGER, revision);
    }

    public static UUID uuid(PersistentDataContainer pdc) {
        String raw = pdc.get(UUID_KEY, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static UUID uuid(ItemStack stack) {
        return stack == null || !stack.hasItemMeta() ? null : uuid(stack.getItemMeta().getPersistentDataContainer());
    }

    public static int revision(PersistentDataContainer pdc) {
        return pdc.getOrDefault(REV, PersistentDataType.INTEGER, 0);
    }

    public static int uses(PersistentDataContainer pdc, int fallback) {
        return pdc.getOrDefault(USES, PersistentDataType.INTEGER, fallback);
    }

    public static void setUses(PersistentDataContainer pdc, int value) {
        pdc.set(USES, PersistentDataType.INTEGER, Math.max(0, value));
    }

    public static UUID owner(PersistentDataContainer pdc) {
        String raw = pdc.get(OWNER, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String ownerName(PersistentDataContainer pdc) {
        return pdc.get(OWNER_NAME, PersistentDataType.STRING);
    }

    public static void owner(PersistentDataContainer pdc, UUID owner, String name) {
        if (owner == null) {
            pdc.remove(OWNER);
            pdc.remove(OWNER_NAME);
            return;
        }
        pdc.set(OWNER, PersistentDataType.STRING, owner.toString());
        pdc.set(OWNER_NAME, PersistentDataType.STRING, name);
    }

    public static String cleanVar(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    public static Map<String, String> vars(PersistentDataContainer pdc) {
        Map<String, String> out = new LinkedHashMap<>();
        PersistentDataContainer vars = pdc.get(VARS, PersistentDataType.TAG_CONTAINER);
        if (vars == null) {
            return out;
        }
        for (NamespacedKey key : vars.getKeys()) {
            String value = vars.get(key, PersistentDataType.STRING);
            if (value != null) {
                out.put(key.getKey(), value);
            }
        }
        return out;
    }

    public static void vars(PersistentDataContainer pdc, Map<String, String> values) {
        PersistentDataContainer vars = pdc.getAdapterContext().newPersistentDataContainer();
        values.forEach((k, v) -> vars.set(new NamespacedKey(NS, cleanVar(k)), PersistentDataType.STRING, v));
        pdc.set(VARS, PersistentDataType.TAG_CONTAINER, vars);
    }

    /** Variables of the stack merged over the definition defaults. */
    public static Map<String, String> effectiveVars(CustomItem def, ItemMeta meta) {
        Map<String, String> out = new LinkedHashMap<>(def.variables);
        if (meta != null) {
            out.putAll(vars(meta.getPersistentDataContainer()));
        }
        return out;
    }
}
