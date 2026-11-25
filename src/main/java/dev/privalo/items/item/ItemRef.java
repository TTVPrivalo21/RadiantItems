package dev.privalo.items.item;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Where a triggered item lives (hand, armour slot, inventory slot, dropped entity...).
 * Delayed actions re-resolve it: if the stack moved, it is searched in the inventory by id/UUID.
 */
public final class ItemRef {

    private final Player player;
    private final Supplier<ItemStack> getter;
    private final Consumer<ItemStack> setter;
    private final String id;
    private final UUID uuid;
    private int fallbackSlot = -1;

    private ItemRef(Player player, Supplier<ItemStack> getter, Consumer<ItemStack> setter, String id, UUID uuid) {
        this.player = player;
        this.getter = getter;
        this.setter = setter;
        this.id = id;
        this.uuid = uuid;
    }

    public static ItemRef of(Player player, Supplier<ItemStack> getter, Consumer<ItemStack> setter, ItemStack current) {
        return new ItemRef(player, getter, setter, ItemData.id(current), ItemData.uuid(current));
    }

    public static ItemRef slot(Player player, EquipmentSlot slot, ItemStack current) {
        PlayerInventory inv = player.getInventory();
        return of(player, () -> inv.getItem(slot), s -> inv.setItem(slot, s), current);
    }

    public static ItemRef index(Player player, int index, ItemStack current) {
        PlayerInventory inv = player.getInventory();
        return of(player, () -> inv.getItem(index), s -> inv.setItem(index, s), current);
    }

    public static ItemRef dropped(Player player, Item entity) {
        ItemStack current = entity.getItemStack();
        return of(player, () -> entity.isValid() ? entity.getItemStack() : null, s -> {
            if (s == null || s.getAmount() <= 0) {
                entity.remove();
            } else if (entity.isValid()) {
                entity.setItemStack(s);
            }
        }, current);
    }

    /** Looks for the item anywhere in the inventory (used for projectiles and fallbacks). */
    public static ItemRef find(Player player, String id, UUID uuid) {
        ItemRef ref = new ItemRef(player, () -> null, s -> {
            // detached reference: writes always go through the located inventory slot
        }, id, uuid);
        return ref.locate() >= 0 ? ref : null;
    }

    public Player player() {
        return player;
    }

    public String id() {
        return id;
    }

    public UUID uuid() {
        return uuid;
    }

    private boolean matches(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || id == null) {
            return false;
        }
        if (!id.equals(ItemData.id(stack))) {
            return false;
        }
        return uuid == null || uuid.equals(ItemData.uuid(stack));
    }

    private int locate() {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (matches(contents[i])) {
                fallbackSlot = i;
                return i;
            }
        }
        fallbackSlot = -1;
        return -1;
    }

    /** Current stack, or null when the item is gone. */
    public ItemStack get() {
        if (fallbackSlot < 0) {
            ItemStack direct = getter.get();
            if (matches(direct)) {
                return direct;
            }
            if (!player.isOnline() || locate() < 0) {
                return null;
            }
        }
        ItemStack stack = player.getInventory().getItem(fallbackSlot);
        if (matches(stack)) {
            return stack;
        }
        return locate() >= 0 ? player.getInventory().getItem(fallbackSlot) : null;
    }

    public void set(ItemStack stack) {
        ItemStack value = stack == null || stack.getAmount() <= 0 ? null : stack;
        if (fallbackSlot >= 0) {
            player.getInventory().setItem(fallbackSlot, value);
        } else {
            setter.accept(value);
        }
    }
}
