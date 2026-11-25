package dev.privalo.items.activator;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.action.ActionRunner;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.item.ItemRef;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Turns Bukkit events into activator triggers. */
public final class TriggerListener implements Listener {

    private final RadiantItems plugin;
    /** Debounce: player+hand → last tick a click was processed (clients sometimes send two packets). */
    private final Map<String, Integer> lastClick = new HashMap<>();

    public TriggerListener(RadiantItems plugin) {
        this.plugin = plugin;
    }

    private ActivatorEngine engine() {
        return plugin.engine();
    }

    // ───────────────────────── clicks ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        ItemStack item = event.getItem();
        if (ItemData.id(item) == null) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();
        String debounceKey = player.getUniqueId() + ":" + hand + ":" + event.getAction();
        int tick = player.getServer().getCurrentTick();
        Integer last = lastClick.put(debounceKey, tick);
        if (last != null && last == tick) {
            return;
        }
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean sneaking = player.isSneaking();
        ActivatorType type = right
                ? (sneaking ? ActivatorType.SHIFT_RIGHT_CLICK : ActivatorType.RIGHT_CLICK)
                : (sneaking ? ActivatorType.SHIFT_LEFT_CLICK : ActivatorType.LEFT_CLICK);
        ItemRef ref = ItemRef.slot(player, hand, item);
        boolean cancel = engine().fire(type, player, ref, ctx -> ctx.block(event.getClickedBlock()).event(event)
                .put("face", event.getBlockFace().name()), null);
        if (cancel) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    // ───────────────────────── combat ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || plugin.guards().actionDamage(player.getUniqueId())) {
            return;
        }
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemData.id(item) == null) {
            return;
        }
        Entity victim = event.getEntity();
        ItemRef ref = ItemRef.slot(player, EquipmentSlot.HAND, item);
        boolean cancel = engine().fire(ActivatorType.HIT_ENTITY, player, ref,
                ctx -> ctx.target(victim).damage(event.getFinalDamage()).event(event), null);
        if (cancel) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        ItemStack item = killer.getInventory().getItemInMainHand();
        if (ItemData.id(item) == null) {
            return;
        }
        ItemRef ref = ItemRef.slot(killer, EquipmentSlot.HAND, item);
        engine().fire(ActivatorType.KILL_ENTITY, killer, ref, ctx -> ctx.target(victim).event(event), null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        String id = ItemData.id(bow);
        CustomItem def = plugin.items().get(id);
        if (def == null) {
            return;
        }
        if (event.getProjectile() instanceof Projectile projectile) {
            ActionRunner.tagProjectile(projectile, def, ItemData.uuid(bow), false);
        }
        ItemRef ref = ItemRef.slot(player, event.getHand(), bow);
        boolean cancel = engine().fire(ActivatorType.SHOOT_BOW, player, ref,
                ctx -> ctx.target(event.getProjectile()).damage(event.getForce()).event(event), null);
        if (cancel) {
            event.setCancelled(true);
        }
    }

    /** Tags tridents/snowballs/eggs thrown with a custom item so PROJECTILE_HIT works for them too. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)
                || projectile.getPersistentDataContainer().has(ItemData.PROJECTILE_ITEM, PersistentDataType.STRING)) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        CustomItem def = plugin.items().get(ItemData.id(main));
        if (def == null) {
            ItemStack off = player.getInventory().getItemInOffHand();
            def = plugin.items().get(ItemData.id(off));
            main = off;
        }
        if (def != null && def.has(ActivatorType.PROJECTILE_HIT)) {
            ActionRunner.tagProjectile(projectile, def, ItemData.uuid(main), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        String id = pdc.get(ItemData.PROJECTILE_ITEM, PersistentDataType.STRING);
        if (id == null || plugin.items().get(id) == null) {
            return;
        }
        String rawUuid = pdc.get(ItemData.PROJECTILE_UUID, PersistentDataType.STRING);
        UUID uuid = null;
        if (rawUuid != null) {
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ignored) {
                uuid = null;
            }
        }
        ItemRef ref = ItemRef.find(player, id, uuid);
        if (ref == null) {
            return;
        }
        Entity hitEntity = event.getHitEntity();
        boolean cancel = engine().fire(ActivatorType.PROJECTILE_HIT, player, ref,
                ctx -> ctx.target(hitEntity).block(event.getHitBlock() != null ? event.getHitBlock() : projectile.getLocation().getBlock()).event(event).put("projectile", projectile.getType().name()), null);
        if (cancel) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── blocks ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.guards().areaBreaking(player.getUniqueId())) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemData.id(item) == null) {
            return;
        }
        ItemRef ref = ItemRef.slot(player, EquipmentSlot.HAND, item);
        boolean cancel = engine().fire(ActivatorType.BLOCK_BREAK, player, ref, ctx -> ctx.block(event.getBlock()).event(event), null);
        if (cancel) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (ItemData.id(item) == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemRef ref = ItemRef.slot(player, event.getHand(), item);
        boolean cancel = engine().fire(ActivatorType.BLOCK_PLACE, player, ref, ctx -> ctx.block(event.getBlockPlaced()).event(event), null);
        if (cancel) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── items ─────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (ItemData.id(item) == null) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemRef ref = ItemRef.slot(player, hand, player.getInventory().getItem(hand));
        boolean cancel = engine().fire(ActivatorType.CONSUME, player, ref, ctx -> ctx.event(event), null);
        if (cancel) {
            event.setCancelled(true);
            // the client already played the eat animation: resync the hand so it shows the real stack
            org.bukkit.Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (ItemData.id(item) == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemRef ref = ItemRef.dropped(player, event.getItemDrop());
        boolean cancel = engine().fire(ActivatorType.DROP_ITEM, player, ref, ctx -> ctx.event(event), null);
        if (cancel) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        ItemStack movingToOffhand = event.getOffHandItem();
        if (ItemData.id(movingToOffhand) == null) {
            return;
        }
        Player player = event.getPlayer();
        // Until the swap happens the item is still in the main hand; write to both so the change
        // survives whether the event ends up cancelled (main hand kept) or not (event's off-hand applied).
        ItemRef ref = ItemRef.of(player, () -> player.getInventory().getItemInMainHand(), s -> {
            event.setOffHandItem(s);
            player.getInventory().setItemInMainHand(s);
        }, movingToOffhand);
        boolean cancel = engine().fire(ActivatorType.SWAP_HANDS, player, ref, ctx -> ctx.event(event), null);
        if (cancel) {
            event.setCancelled(true);
        }
    }

    // ───────────────────────── equipped (hands + armour) ─────────────────────────

    private static final EquipmentSlot[] EQUIPPED = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Fires {@code type} for every custom item in hands/armour whose activator slot matches. */
    private boolean fireEquipped(ActivatorType type, Player player, java.util.function.Consumer<ActivationContext> init) {
        boolean cancel = false;
        for (EquipmentSlot slot : EQUIPPED) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemData.id(stack) == null) {
                continue;
            }
            ItemRef ref = ItemRef.slot(player, slot, stack);
            cancel |= engine().fire(type, player, ref, init, a -> LoopTask.slotMatches(a.slot, slot));
        }
        return cancel;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        if (fireEquipped(ActivatorType.SNEAK, event.getPlayer(), ctx -> ctx.event(event))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (fireEquipped(ActivatorType.JUMP, event.getPlayer(), ctx -> ctx.event(event))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }
        if (attacker instanceof Player p && plugin.guards().actionDamage(p.getUniqueId())) {
            return;
        }
        Entity source = attacker;
        if (fireEquipped(ActivatorType.DAMAGED, victim, ctx -> ctx.target(source).damage(event.getFinalDamage()).event(event))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || ItemData.id(event.getItem().getItemStack()) == null) {
            return;
        }
        ItemRef ref = ItemRef.dropped(player, event.getItem());
        if (engine().fire(ActivatorType.PICKUP_ITEM, player, ref, ctx -> ctx.event(event), null)) {
            event.setCancelled(true);
        }
    }

    /** DEATH runs before drops are processed; the killer (if any) is the target. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        Entity killer = player.getKiller();
        if (killer == null && player.getLastDamageCause() instanceof EntityDamageByEntityEvent by) {
            killer = by.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity shooter ? shooter : by.getDamager();
        }
        Entity source = killer;
        fireEquipped(ActivatorType.DEATH, player, ctx -> ctx.target(source).event(event));
    }

    public void forget(UUID player) {
        String prefix = player.toString();
        lastClick.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
