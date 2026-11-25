package dev.privalo.items.listener;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Restrictions (craft/smelt/anvil/drop), keep-on-death, recharge, safe explosions and no-fall windows. */
public final class ItemListener implements Listener {

    private final RadiantItems plugin;
    private final Map<UUID, List<ItemStack>> keptOnDeath = new HashMap<>();

    public ItemListener(RadiantItems plugin) {
        this.plugin = plugin;
    }

    private CustomItem def(ItemStack stack) {
        return plugin.items().get(ItemData.id(stack));
    }

    // ───────────────────────── restrictions ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            CustomItem def = def(stack);
            if (def != null && !def.canCraft) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmelt(FurnaceSmeltEvent event) {
        CustomItem def = def(event.getSource());
        if (def != null && !def.canSmelt) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        CustomItem def = def(event.getFuel());
        if (def != null && !def.canSmelt) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        Inventory inv = event.getInventory();
        for (int i = 0; i < 2; i++) {
            CustomItem def = def(inv.getItem(i));
            if (def != null && !def.canAnvil) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        for (ItemStack stack : event.getInventory().getContents()) {
            CustomItem def = def(stack);
            if (def != null && !def.canCraft) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        CustomItem def = def(event.getItemDrop().getItemStack());
        if (def != null && !def.canDrop) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "drop-blocked");
        }
    }

    // ───────────────────────── no-store ─────────────────────────

    /** Inventories that only process items (crafting, anvils...) are governed by the other restrictions. */
    private static final java.util.Set<org.bukkit.event.inventory.InventoryType> NOT_STORAGE = java.util.EnumSet.of(
            org.bukkit.event.inventory.InventoryType.CRAFTING, org.bukkit.event.inventory.InventoryType.PLAYER,
            org.bukkit.event.inventory.InventoryType.CREATIVE, org.bukkit.event.inventory.InventoryType.WORKBENCH,
            org.bukkit.event.inventory.InventoryType.ANVIL, org.bukkit.event.inventory.InventoryType.ENCHANTING,
            org.bukkit.event.inventory.InventoryType.GRINDSTONE, org.bukkit.event.inventory.InventoryType.SMITHING,
            org.bukkit.event.inventory.InventoryType.STONECUTTER, org.bukkit.event.inventory.InventoryType.LOOM,
            org.bukkit.event.inventory.InventoryType.CARTOGRAPHY, org.bukkit.event.inventory.InventoryType.MERCHANT);

    private boolean storeBlocked(ItemStack stack) {
        CustomItem def = def(stack);
        return def != null && !def.canStore;
    }

    /** no-store: the item can never be put into chests, barrels, shulkers, ender chests, hoppers... */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStore(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof dev.privalo.items.gui.Menu || NOT_STORAGE.contains(top.getType())
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean clickedTop = top.equals(event.getClickedInventory());
        boolean blocked = false;
        if (clickedTop && storeBlocked(event.getCursor())) {
            blocked = true;
        } else if (!clickedTop && event.isShiftClick() && storeBlocked(event.getCurrentItem())) {
            blocked = true;
        } else if (clickedTop && event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY
                && storeBlocked(player.getInventory().getItem(event.getHotbarButton()))) {
            blocked = true;
        } else if (clickedTop && event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && storeBlocked(player.getInventory().getItemInOffHand())) {
            blocked = true;
        }
        if (blocked) {
            event.setCancelled(true);
            plugin.messages().send(player, "store-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStoreDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof dev.privalo.items.gui.Menu || NOT_STORAGE.contains(top.getType()) || !storeBlocked(event.getOldCursor())) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < top.getSize()) {
                event.setCancelled(true);
                plugin.messages().send(event.getWhoClicked(), "store-blocked");
                return;
            }
        }
    }

    /** Hoppers / hopper minecarts never swallow no-store or soulbound items lying on the ground. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperPickup(org.bukkit.event.inventory.InventoryPickupItemEvent event) {
        CustomItem def = def(event.getItem().getItemStack());
        if (def != null && (!def.canStore || def.soulbound)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(org.bukkit.event.inventory.InventoryMoveItemEvent event) {
        if (storeBlocked(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Soulbound items can only be picked up by their owner (or with the bypass permission). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSoulboundPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            CustomItem def = def(event.getItem().getItemStack());
            if (def != null && def.soulbound) {
                event.setCancelled(true);
            }
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        CustomItem def = def(stack);
        if (def == null || !def.soulbound) {
            return;
        }
        UUID owner = ItemData.owner(stack.getItemMeta().getPersistentDataContainer());
        if (owner != null && !owner.equals(player.getUniqueId()) && !player.hasPermission("radiantitems.bypass.soulbound")) {
            event.setCancelled(true);
            if (plugin.pickupNotice(player.getUniqueId())) {
                plugin.messages().send(player, "pickup-blocked", "owner", ItemData.ownerName(stack.getItemMeta().getPersistentDataContainer()));
            }
        }
    }

    // ───────────────────────── recharge ─────────────────────────

    /** Clicking an item with its recharge material on the cursor refills uses. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRecharge(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getInventory().getHolder() instanceof dev.privalo.items.gui.Menu) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        CustomItem def = def(clicked);
        if (def == null || def.maxUses <= 0 || def.rechargeMaterial == null || cursor == null || cursor.getType().isAir()) {
            return;
        }
        Material material = Material.matchMaterial(def.rechargeMaterial);
        if (material == null || cursor.getType() != material || ItemData.id(cursor) != null) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int uses = ItemData.uses(pdc, def.maxUses);
        if (uses >= def.maxUses) {
            return;
        }
        event.setCancelled(true);
        int next = Math.min(def.maxUses, uses + def.rechargeUses);
        ItemData.setUses(pdc, next);
        plugin.factory().render(def, meta, player);
        clicked.setItemMeta(meta);
        event.setCurrentItem(clicked);
        cursor.setAmount(cursor.getAmount() - 1);
        player.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        plugin.messages().send(player, "recharged", "uses", String.valueOf(next), "max_uses", String.valueOf(def.maxUses));
    }

    // ───────────────────────── death ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        List<ItemStack> kept = new ArrayList<>();
        Iterator<ItemStack> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            CustomItem def = def(stack);
            if (def != null && (def.keepOnDeath || def.soulbound)) {
                kept.add(stack);
                it.remove();
            }
        }
        if (!kept.isEmpty()) {
            keptOnDeath.computeIfAbsent(event.getEntity().getUniqueId(), k -> new ArrayList<>()).addAll(kept);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        returnKept(event.getPlayer());
    }

    private void returnKept(Player player) {
        List<ItemStack> kept = keptOnDeath.remove(player.getUniqueId());
        if (kept == null || kept.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                keptOnDeath.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).addAll(kept);
                return;
            }
            player.getInventory().addItem(kept.toArray(new ItemStack[0]))
                    .values().forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
        });
    }

    // ───────────────────────── explosions / fall ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isSafe(event.getEntity())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (isSafe(event.getEntity())) {
            event.setFire(false);
        }
    }

    private static boolean isSafe(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(ItemData.SAFE, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSelfExplosion(EntityDamageByEntityEvent event) {
        if (event.getDamager() == event.getEntity()
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)
                && plugin.guards().safeExplosion(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Fireworks and TNT launched by items are cosmetic for players: no damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if ((damager instanceof org.bukkit.entity.Firework || damager instanceof org.bukkit.entity.TNTPrimed)
                && event.getEntity() instanceof Player && isSafe(damager)) {
            event.setCancelled(true);
        }
    }

    /** Mobs thrown by LAUNCH_ENTITY never drop loot or experience. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTemporaryDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(ItemData.TEMPORARY, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && plugin.guards().consumeNoFall(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── join / quit ─────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.loop().updateOutdated(player);
                returnKept(player);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.guards().clear(id);
        plugin.loop().forget(id);
        plugin.triggers().forget(id);
        plugin.chat().cancel(event.getPlayer());
        plugin.forgetPickupNotice(id);
    }
}
