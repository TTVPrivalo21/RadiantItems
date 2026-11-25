package dev.privalo.items.activator;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.item.ItemRef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Periodic scanner: EQUIP / UNEQUIP / HOLD_LOOP on hands + armour, live %cooldown% lore and
 * rebuilding stacks whose definition changed (revision mismatch).
 */
public final class LoopTask implements Runnable {

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final RadiantItems plugin;
    private final Map<UUID, Map<EquipmentSlot, String>> previous = new HashMap<>();
    private BukkitTask task;
    private int period = 5;
    private int updatePeriod = 100;
    private long ticks;

    public LoopTask(RadiantItems plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        period = Math.max(1, plugin.getConfig().getInt("loop.interval-ticks", 5));
        updatePeriod = Math.max(20, plugin.getConfig().getInt("loop.update-interval-ticks", 100));
        ticks = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void forget(UUID player) {
        previous.remove(player);
    }

    @Override
    public void run() {
        ticks += period;
        boolean lore = ticks % 20 < period;
        boolean update = ticks % updatePeriod < period;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                scan(player, lore);
                if (update) {
                    updateOutdated(player);
                }
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Error en el bucle de ítems de " + player.getName() + ": " + ex.getMessage());
            }
        }
        if (update) {
            plugin.cooldowns().purge();
        }
    }

    private void scan(Player player, boolean refreshLore) {
        PlayerInventory inv = player.getInventory();
        Map<EquipmentSlot, String> before = previous.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(EquipmentSlot.class));
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = inv.getItem(slot);
            String id = ItemData.id(stack);
            UUID uuid = id == null ? null : ItemData.uuid(stack);
            String fingerprint = id == null ? null : id + "|" + uuid;
            String old = before.get(slot);
            if (!java.util.Objects.equals(old, fingerprint)) {
                if (old != null) {
                    unequip(player, slot, old);
                }
                if (fingerprint == null) {
                    before.remove(slot);
                } else {
                    before.put(slot, fingerprint);
                    fire(player, slot, stack, ActivatorType.EQUIP);
                }
            }
            if (id == null) {
                continue;
            }
            CustomItem def = plugin.items().get(id);
            if (def == null) {
                continue;
            }
            if (def.has(ActivatorType.HOLD_LOOP)) {
                ItemStack current = inv.getItem(slot);
                if (ItemData.id(current) != null) {
                    ItemRef ref = ItemRef.slot(player, slot, current);
                    plugin.engine().fire(ActivatorType.HOLD_LOOP, player, ref, null,
                            a -> slotMatches(a.slot, slot) && ticks % roundUp(a.interval) < period);
                }
            }
            if (refreshLore && def.loreUses("%cooldown%")) {
                ItemStack current = inv.getItem(slot);
                if (current != null && id.equals(ItemData.id(current))) {
                    ItemMeta meta = current.getItemMeta();
                    plugin.factory().render(def, meta, player);
                    current.setItemMeta(meta);
                    inv.setItem(slot, current);
                }
            }
        }
    }

    private long roundUp(int interval) {
        long rounded = ((interval + period - 1) / period) * (long) period;
        return Math.max(period, rounded);
    }

    private void fire(Player player, EquipmentSlot slot, ItemStack stack, ActivatorType type) {
        ItemRef ref = ItemRef.slot(player, slot, stack);
        plugin.engine().fire(type, player, ref, null, a -> slotMatches(a.slot, slot));
    }

    private void unequip(Player player, EquipmentSlot slot, String fingerprint) {
        String id = fingerprint.substring(0, fingerprint.indexOf('|'));
        String rawUuid = fingerprint.substring(fingerprint.indexOf('|') + 1);
        CustomItem def = plugin.items().get(id);
        if (def == null || !def.has(ActivatorType.UNEQUIP)) {
            return;
        }
        UUID uuid = rawUuid.equals("null") ? null : UUID.fromString(rawUuid);
        ItemRef ref = ItemRef.find(player, id, uuid);
        if (ref == null) {
            // the item left the inventory entirely: run without a live stack using a detached copy
            ItemStack ghost = plugin.factory().create(def, 1);
            ref = ItemRef.of(player, () -> ghost, s -> {
                // the real stack is gone: state changes on the ghost copy are intentionally discarded
            }, ghost);
        }
        plugin.engine().fire(ActivatorType.UNEQUIP, player, ref, null, a -> slotMatches(a.slot, slot));
    }

    public static boolean slotMatches(Activator.SlotMode mode, EquipmentSlot slot) {
        return switch (mode) {
            case MAIN_HAND -> slot == EquipmentSlot.HAND;
            case OFF_HAND -> slot == EquipmentSlot.OFF_HAND;
            case HAND -> slot == EquipmentSlot.HAND || slot == EquipmentSlot.OFF_HAND;
            case ARMOR -> slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
            case ANY -> true;
        };
    }

    /** Rebuilds every stack in the inventory whose stored revision differs from its definition. */
    public void updateOutdated(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            String id = ItemData.id(stack);
            if (id == null) {
                continue;
            }
            CustomItem def = plugin.items().get(id);
            if (def == null) {
                continue;
            }
            if (ItemData.revision(stack.getItemMeta().getPersistentDataContainer()) != plugin.factory().revision(def)) {
                inv.setItem(i, plugin.factory().rebuild(stack, def));
            }
        }
    }
}
