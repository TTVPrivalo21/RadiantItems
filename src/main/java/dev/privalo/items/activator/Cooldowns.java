package dev.privalo.items.activator;

import dev.privalo.items.item.CustomItem;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cooldowns per player, per item copy (UUID) or global, keyed by item id + activator key. */
public final class Cooldowns {

    private final Map<String, Long> until = new HashMap<>();

    public static String key(Activator a, CustomItem def, Player player, UUID itemUuid) {
        return switch (a.scope) {
            case GLOBAL -> "g:" + def.id + ":" + a.key;
            case ITEM -> itemUuid != null ? "i:" + itemUuid + ":" + a.key : "p:" + player.getUniqueId() + ":" + def.id + ":" + a.key;
            case PLAYER -> "p:" + player.getUniqueId() + ":" + def.id + ":" + a.key;
        };
    }

    /** Remaining seconds (0 when ready). */
    public double remaining(String key) {
        Long end = until.get(key);
        if (end == null) {
            return 0;
        }
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            until.remove(key);
            return 0;
        }
        return left / 1000.0;
    }

    public void set(String key, double seconds) {
        if (seconds > 0) {
            until.put(key, System.currentTimeMillis() + Math.round(seconds * 1000));
        }
    }

    /** Longest remaining cooldown among the item's activators for this holder. */
    public double remaining(CustomItem def, Player holder, UUID itemUuid) {
        if (holder == null) {
            return 0;
        }
        double max = 0;
        for (Activator a : def.activators) {
            if (a.cooldown > 0) {
                max = Math.max(max, remaining(key(a, def, holder, itemUuid)));
            }
        }
        return max;
    }

    public void reset(Player player) {
        String prefix = "p:" + player.getUniqueId() + ":";
        until.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void purge() {
        long now = System.currentTimeMillis();
        until.values().removeIf(end -> end <= now);
    }
}
