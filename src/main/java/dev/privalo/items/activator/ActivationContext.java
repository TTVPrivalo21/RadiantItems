package dev.privalo.items.activator;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.item.ItemRef;
import dev.privalo.items.util.Expr;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;

/** Everything an activation knows: who, which item, where it lives, target/block/damage of the event. */
public final class ActivationContext {

    private final RadiantItems plugin;
    private final Player player;
    private final CustomItem item;
    private final ItemRef ref;
    private final ActivatorType trigger;
    private Activator activator;
    private Entity target;
    private Block block;
    private double damage;
    private Event event;
    private final Map<String, String> extra = new HashMap<>();

    public ActivationContext(RadiantItems plugin, Player player, CustomItem item, ItemRef ref, ActivatorType trigger) {
        this.plugin = plugin;
        this.player = player;
        this.item = item;
        this.ref = ref;
        this.trigger = trigger;
    }

    public RadiantItems plugin() {
        return plugin;
    }

    public Player player() {
        return player;
    }

    public CustomItem item() {
        return item;
    }

    public ItemRef ref() {
        return ref;
    }

    public ActivatorType trigger() {
        return trigger;
    }

    public Activator activator() {
        return activator;
    }

    public ActivationContext activator(Activator value) {
        this.activator = value;
        return this;
    }

    public Entity target() {
        return target;
    }

    public ActivationContext target(Entity value) {
        this.target = value;
        return this;
    }

    public Block block() {
        return block;
    }

    public ActivationContext block(Block value) {
        this.block = value;
        return this;
    }

    public double damage() {
        return damage;
    }

    public ActivationContext damage(double value) {
        this.damage = value;
        return this;
    }

    public Event event() {
        return event;
    }

    public ActivationContext event(Event value) {
        this.event = value;
        return this;
    }

    public ActivationContext put(String key, String value) {
        extra.put(key, value);
        return this;
    }

    /** Copy for one activator (activators of the same event never share state). */
    public ActivationContext fork(Activator a) {
        ActivationContext copy = new ActivationContext(plugin, player, item, ref, trigger);
        copy.target = target;
        copy.block = block;
        copy.damage = damage;
        copy.event = event;
        copy.extra.putAll(extra);
        copy.activator = a;
        return copy;
    }

    /** Remaining uses of the live stack (max-uses when unknown). */
    public int uses() {
        ItemStack stack = ref == null ? null : ref.get();
        if (stack == null || item.maxUses <= 0) {
            return item.maxUses;
        }
        return ItemData.uses(stack.getItemMeta().getPersistentDataContainer(), item.maxUses);
    }

    /** Context block, or the block the player is looking at (up to {@code range}). */
    public Location blockOrLook(double range) {
        if (block != null) {
            return block.getLocation().add(0.5, 0.5, 0.5);
        }
        RayTraceResult hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                range, FluidCollisionMode.NEVER, true);
        if (hit != null && hit.getHitBlock() != null) {
            return hit.getHitPosition().toLocation(player.getWorld());
        }
        return player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(range));
    }

    public String format(String raw) {
        return format(raw, target);
    }

    /** Replaces internal placeholders (+ PlaceholderAPI when installed). */
    public String format(String raw, Entity forTarget) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String out = raw;
        if (out.indexOf('%') >= 0) {
            Location loc = player.getLocation();
            out = out.replace("%player%", player.getName())
                    .replace("%player_uuid%", player.getUniqueId().toString())
                    .replace("%world%", player.getWorld().getName())
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%y%", String.valueOf(loc.getBlockY()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()))
                    .replace("%health%", Expr.format(player.getHealth()))
                    .replace("%food%", String.valueOf(player.getFoodLevel()))
                    .replace("%level%", String.valueOf(player.getLevel()))
                    .replace("%damage%", Expr.format(damage))
                    .replace("%item%", item.displayName())
                    .replace("%item_id%", item.id);
            if (out.contains("%target")) {
                out = out.replace("%target%", forTarget == null ? "" : forTarget instanceof Player p ? p.getName() : forTarget.getName())
                        .replace("%target_type%", forTarget == null ? "" : forTarget.getType().name());
                Location t = forTarget != null ? forTarget.getLocation() : block != null ? block.getLocation() : null;
                out = out.replace("%target_x%", t == null ? "" : String.valueOf(t.getBlockX()))
                        .replace("%target_y%", t == null ? "" : String.valueOf(t.getBlockY()))
                        .replace("%target_z%", t == null ? "" : String.valueOf(t.getBlockZ()));
            }
            if (out.contains("%block")) {
                out = out.replace("%block%", block == null ? "" : block.getType().name());
            }
            for (Map.Entry<String, String> e : extra.entrySet()) {
                out = out.replace("%" + e.getKey() + "%", e.getValue());
            }
            if (out.indexOf('%') >= 0 && ref != null) {
                ItemStack stack = ref.get();
                ItemMeta meta = stack == null ? null : stack.getItemMeta();
                if (meta != null) {
                    out = plugin.factory().placeholders(item, meta, player, out);
                } else {
                    for (Map.Entry<String, String> e : item.variables.entrySet()) {
                        out = out.replace("%" + e.getKey() + "%", e.getValue());
                    }
                }
            }
        }
        return plugin.hooks().placeholders(player, out);
    }
}
