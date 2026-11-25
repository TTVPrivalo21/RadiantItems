package dev.privalo.items.action;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.activator.ActivationContext;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.util.Expr;
import dev.privalo.items.util.Messages;
import dev.privalo.items.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Second batch of actions: magnets, detectors, per-player time/weather, entity launching, fireworks,
 * homing projectiles, vein mining, harvesting, building, repairing, weighted loot, quick-access GUIs
 * and wearing items as hats.
 */
final class ExtraActions {

    /** Crops that HARVEST breaks when fully grown and replants. */
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART,
            Material.COCOA, Material.SWEET_BERRY_BUSH);

    private final RadiantItems plugin;
    private final ActionRunner runner;

    ExtraActions(RadiantItems plugin, ActionRunner runner) {
        this.plugin = plugin;
        this.runner = runner;
    }

    void execute(Action action, ActivationContext ctx, String args, String[] p) {
        switch (action.type()) {
            case MAGNET -> magnet(action, ctx, p);
            case DETECT -> detect(ctx, p);
            case PLAYER_TIME -> playerTime(action, ctx, p[0]);
            case PLAYER_WEATHER -> playerWeather(action, ctx, p[0]);
            case LAUNCH_ENTITY -> launchEntity(ctx, p);
            case FIREWORK -> firework(action, ctx, p);
            case HOMING -> homing(action, ctx, p);
            case VEIN_MINE -> veinMine(ctx, p);
            case HARVEST -> harvest(ctx, p);
            case BUILD_LINE -> buildLine(ctx, p);
            case REPAIR -> repair(action, ctx, p);
            case LOOT -> loot(action, ctx);
            case OPEN -> open(ctx.player(), args.trim().toLowerCase(Locale.ROOT));
            case WEAR -> wear(ctx);
            default -> throw new IllegalStateException("ExtraActions no gestiona " + action.type());
        }
    }

    // ───────────────────────── utility ─────────────────────────

    private void magnet(Action action, ActivationContext ctx, String[] p) {
        double radius = Math.min(32, Expr.num(p[0], 8));
        double strength = p.length > 1 ? Expr.num(p[1], 0.6) : 0.6;
        for (Player player : runner.players(action, ctx)) {
            Location center = player.getLocation().add(0, 0.6, 0);
            for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
                if (!(e instanceof Item) && !(e instanceof ExperienceOrb)) {
                    continue;
                }
                Vector pull = center.toVector().subtract(e.getLocation().toVector());
                double distance = pull.length();
                if (distance < 0.6 || distance > radius) {
                    continue;
                }
                e.setVelocity(pull.normalize().multiply(Math.min(1.5, strength + distance * 0.04)));
            }
        }
    }

    private void detect(ActivationContext ctx, String[] p) {
        Player player = ctx.player();
        String mode = p[0].toLowerCase(Locale.ROOT);
        int radius = (int) Math.max(1, Math.min(mode.equals("ores") ? 12 : 48, p.length > 1 ? Expr.num(p[1], 8) : 8));
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (mode.equals("ores")) {
            Location origin = player.getLocation();
            World world = player.getWorld();
            List<Location> found = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block b = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                        String name = b.getType().name();
                        if (name.endsWith("_ORE") || b.getType() == Material.ANCIENT_DEBRIS) {
                            String key = Text.pretty(name.replace("DEEPSLATE_", "").replace("NETHER_", "").replace("_ORE", ""));
                            counts.merge(key, 1, Integer::sum);
                            found.add(b.getLocation().add(0.5, 0.5, 0.5));
                        }
                    }
                }
            }
            found.sort(Comparator.comparingDouble(l -> l.distanceSquared(origin)));
            for (Location l : found.subList(0, Math.min(6, found.size()))) {
                player.spawnParticle(Particle.WAX_ON, l, 6, 0.3, 0.3, 0.3, 0);
            }
        } else {
            for (Entity e : player.getNearbyEntities(radius, radius, radius)) {
                if (e instanceof LivingEntity && e != player) {
                    counts.merge(Text.pretty(e.getType().name()), 1, Integer::sum);
                }
            }
        }
        if (counts.isEmpty()) {
            plugin.engine().message(ctx, "detect-none", "radius", String.valueOf(radius));
            return;
        }
        StringBuilder list = new StringBuilder();
        counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(5).forEach(e -> {
            if (!list.isEmpty()) {
                list.append(", ");
            }
            list.append(e.getKey()).append(" x").append(e.getValue());
        });
        String template = plugin.messages().rawOrNull("activator." + (mode.equals("ores") ? "detect-ores" : "detect-entities"));
        if (template != null && !template.isEmpty()) {
            player.sendActionBar(Text.parse(Messages.apply(template, "list", list.toString(), "radius", String.valueOf(radius))));
        }
    }

    private void playerTime(Action action, ActivationContext ctx, String raw) {
        String mode = raw.toLowerCase(Locale.ROOT);
        for (Player t : runner.players(action, ctx)) {
            switch (mode) {
                case "reset" -> t.resetPlayerTime();
                case "day" -> t.setPlayerTime(1000, false);
                case "noon" -> t.setPlayerTime(6000, false);
                case "sunset" -> t.setPlayerTime(12500, false);
                case "night" -> t.setPlayerTime(14000, false);
                case "midnight" -> t.setPlayerTime(18000, false);
                default -> t.setPlayerTime((long) Expr.num(raw, 1000), false);
            }
        }
    }

    private void playerWeather(Action action, ActivationContext ctx, String raw) {
        for (Player t : runner.players(action, ctx)) {
            switch (raw.toLowerCase(Locale.ROOT)) {
                case "clear" -> t.setPlayerWeather(WeatherType.CLEAR);
                case "rain" -> t.setPlayerWeather(WeatherType.DOWNFALL);
                default -> t.resetPlayerWeather();
            }
        }
    }

    private void open(Player player, String type) {
        Location at = player.getLocation();
        switch (type) {
            case "workbench" -> player.openWorkbench(at, true);
            case "enderchest" -> player.openInventory(player.getEnderChest());
            case "enchanting" -> player.openEnchanting(at, true);
            case "anvil" -> player.openAnvil(at, true);
            case "grindstone" -> player.openGrindstone(at, true);
            case "stonecutter" -> player.openStonecutter(at, true);
            case "loom" -> player.openLoom(at, true);
            case "smithing" -> player.openSmithingTable(at, true);
            case "cartography" -> player.openCartographyTable(at, true);
            default -> throw new IllegalArgumentException("OPEN desconocido: " + type);
        }
    }

    /** Swaps the item with the helmet slot (hats, crowns, decorative heads). */
    private void wear(ActivationContext ctx) {
        if (ctx.ref() == null) {
            return;
        }
        ItemStack stack = ctx.ref().get();
        if (stack == null) {
            return;
        }
        PlayerInventory inv = ctx.player().getInventory();
        ItemStack helmet = inv.getHelmet();
        ItemStack worn = stack.clone();
        worn.setAmount(1);
        inv.setHelmet(worn);
        stack.setAmount(stack.getAmount() - 1);
        ctx.ref().set(stack);
        if (helmet != null && !helmet.getType().isAir()) {
            inv.addItem(helmet).values().forEach(s -> ctx.player().getWorld().dropItemNaturally(ctx.player().getLocation(), s));
        }
        ctx.player().playSound(ctx.player().getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.2f);
    }

    private void repair(Action action, ActivationContext ctx, String[] p) {
        boolean all = p[0].equalsIgnoreCase("all") || p[0].equalsIgnoreCase("full");
        int amount = all ? Integer.MAX_VALUE : (int) Math.max(1, Expr.num(p[0], 50));
        String where = p.length > 1 ? p[1].toLowerCase(Locale.ROOT) : "offhand";
        for (Player t : runner.players(action, ctx)) {
            PlayerInventory inv = t.getInventory();
            List<Integer> slots = new ArrayList<>();
            switch (where) {
                case "offhand" -> slots.add(40);
                case "hand", "mainhand" -> slots.add(inv.getHeldItemSlot());
                case "armor" -> slots.addAll(List.of(36, 37, 38, 39));
                case "hotbar" -> {
                    for (int i = 0; i < 9; i++) {
                        slots.add(i);
                    }
                }
                default -> {
                    for (int i = 0; i < 41; i++) {
                        slots.add(i);
                    }
                }
            }
            int repaired = 0;
            for (int slot : slots) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType().isAir() || ItemData.id(stack) != null && ItemData.id(stack).equals(ctx.item().id)) {
                    continue;
                }
                ItemMeta meta = stack.getItemMeta();
                if (meta instanceof Damageable dmg && dmg.hasDamage()) {
                    dmg.setDamage(Math.max(0, dmg.getDamage() - amount));
                    stack.setItemMeta(meta);
                    inv.setItem(slot, stack);
                    repaired++;
                }
            }
            plugin.engine().message(ctx, "repaired", "count", String.valueOf(repaired));
        }
    }

    // ───────────────────────── fun ─────────────────────────

    private void launchEntity(ActivationContext ctx, String[] p) {
        Player player = ctx.player();
        EntityType type;
        try {
            type = EntityType.valueOf(p[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("entidad desconocida " + p[0]);
        }
        if (!type.isSpawnable() || type == EntityType.PLAYER) {
            throw new IllegalArgumentException("no se puede lanzar " + type);
        }
        double speed = p.length > 1 ? Expr.num(p[1], 1.2) : 1.2;
        long lifetime = p.length > 2 ? Math.max(20, (long) Expr.num(p[2], 100)) : 100;
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Entity entity = player.getWorld().spawnEntity(eye.clone().add(dir.clone().multiply(1.2)), type);
        entity.setVelocity(dir.multiply(speed).add(new Vector(0, 0.15, 0)));
        entity.getPersistentDataContainer().set(ItemData.SAFE, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(ItemData.TEMPORARY, PersistentDataType.BYTE, (byte) 1);
        if (entity instanceof TNTPrimed tnt) {
            tnt.setFuseTicks((int) Math.min(lifetime, 80));
            tnt.setSource(player);
        }
        if (entity instanceof Mob mob) {
            mob.setPersistent(false);
            mob.setRemoveWhenFarAway(true);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid()) {
                entity.getWorld().spawnParticle(Particle.POOF, entity.getLocation(), 12, 0.3, 0.3, 0.3, 0.02);
                entity.remove();
            }
        }, lifetime);
    }

    private void firework(Action action, ActivationContext ctx, String[] p) {
        FireworkEffect.Type type;
        try {
            type = FireworkEffect.Type.valueOf(p[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            type = FireworkEffect.Type.BALL;
        }
        List<Color> colors = new ArrayList<>();
        if (p.length > 1) {
            for (String c : p[1].split(",")) {
                colors.add(Text.color(c, Color.ORANGE));
            }
        }
        if (colors.isEmpty()) {
            colors.add(Color.fromRGB(0xFB923C));
            colors.add(Color.fromRGB(0xF97316));
        }
        int power = p.length > 2 ? (int) Math.max(0, Math.min(3, Expr.num(p[2], 1))) : 1;
        boolean instant = p.length > 3 && p[3].equalsIgnoreCase("instant");
        FireworkEffect effect = FireworkEffect.builder().with(type).withColor(colors).withFade(Color.WHITE)
                .trail(true).flicker(ThreadLocalRandom.current().nextBoolean()).build();
        for (Location loc : runner.locations(action, ctx)) {
            Firework fw = loc.getWorld().spawn(loc.clone().add(0, 0.5, 0), Firework.class, f -> {
                FireworkMeta meta = f.getFireworkMeta();
                meta.addEffect(effect);
                meta.setPower(power);
                f.setFireworkMeta(meta);
                f.getPersistentDataContainer().set(ItemData.SAFE, PersistentDataType.BYTE, (byte) 1);
            });
            if (instant) {
                Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 1L);
            }
        }
    }

    /** Steers the target projectile (SHOOT_BOW sets it) towards the nearest living entity. */
    private void homing(Action action, ActivationContext ctx, String[] p) {
        double radius = Math.min(32, Expr.num(p[0], 12));
        int ticks = p.length > 1 ? (int) Math.max(5, Math.min(200, Expr.num(p[1], 60))) : 60;
        Player shooter = ctx.player();
        for (Entity e : runner.entities(action, ctx)) {
            if (!(e instanceof Projectile projectile)) {
                continue;
            }
            new BukkitRunnable() {
                private int lived;

                @Override
                public void run() {
                    lived += 2;
                    if (!projectile.isValid() || projectile.isOnGround() || lived > ticks) {
                        cancel();
                        return;
                    }
                    LivingEntity best = null;
                    double bestDistance = radius * radius;
                    for (Entity near : projectile.getNearbyEntities(radius, radius, radius)) {
                        if (near instanceof LivingEntity le && near != shooter && !le.isDead()
                                && !(near instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR)) {
                            double d = near.getLocation().distanceSquared(projectile.getLocation());
                            if (d < bestDistance) {
                                bestDistance = d;
                                best = le;
                            }
                        }
                    }
                    if (best == null) {
                        return;
                    }
                    Vector velocity = projectile.getVelocity();
                    double speed = Math.max(0.6, velocity.length());
                    Vector desired = best.getEyeLocation().subtract(0, 0.4, 0).toVector().subtract(projectile.getLocation().toVector()).normalize();
                    Vector steered = velocity.clone().normalize().multiply(0.65).add(desired.multiply(0.35)).normalize().multiply(speed);
                    projectile.setVelocity(steered);
                    projectile.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, projectile.getLocation(), 1, 0, 0, 0, 0);
                }
            }.runTaskTimer(plugin, 2L, 2L);
        }
    }

    // ───────────────────────── blocks ─────────────────────────

    private boolean breakable(Block block, Player player, List<String> blacklist, double maxHardness) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid() || blacklist.contains(type.name())) {
            return false;
        }
        float hardness = type.getHardness();
        return hardness >= 0 && hardness <= maxHardness && !player.getInventory().getItemInMainHand().getType().isAir();
    }

    /** Breaks every connected block of the same type (ores, logs...) through BlockBreakEvent. */
    private void veinMine(ActivationContext ctx, String[] p) {
        Block origin = ctx.block();
        Player player = ctx.player();
        if (origin == null) {
            return;
        }
        int max = (int) Math.max(1, Math.min(128, Expr.num(p[0], 32)));
        Material type = origin.getType();
        double maxHardness = plugin.getConfig().getDouble("mine-area.max-hardness", 50);
        List<String> blacklist = plugin.getConfig().getStringList("mine-area.blacklist");
        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        queue.add(origin);
        seen.add(origin);
        List<Block> targets = new ArrayList<>();
        while (!queue.isEmpty() && targets.size() < max) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block next = current.getRelative(dx, dy, dz);
                        if (next.getType() == type && seen.add(next)) {
                            queue.add(next);
                            targets.add(next);
                        }
                    }
                }
            }
        }
        int broken = 1;
        plugin.guards().areaBreaking(player.getUniqueId(), true);
        try {
            for (Block block : targets.subList(0, Math.min(max, targets.size()))) {
                if (breakable(block, player, blacklist, maxHardness) && player.breakBlock(block)) {
                    broken++;
                }
            }
        } finally {
            plugin.guards().areaBreaking(player.getUniqueId(), false);
            ctx.put("broken", String.valueOf(broken));
        }
    }

    /** Breaks fully grown crops around the block (or the player) and replants them. */
    private void harvest(ActivationContext ctx, String[] p) {
        Player player = ctx.player();
        int radius = (int) Math.max(0, Math.min(6, Expr.num(p[0], 2)));
        Block center = ctx.block() != null ? ctx.block() : player.getLocation().getBlock();
        int harvested = 0;
        plugin.guards().areaBreaking(player.getUniqueId(), true);
        try {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block block = center.getRelative(x, y, z);
                        Material type = block.getType();
                        if (!CROPS.contains(type) || !(block.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) {
                            continue;
                        }
                        org.bukkit.block.data.BlockData original = block.getBlockData().clone();
                        if (player.breakBlock(block)) {
                            Ageable replant = (Ageable) original;
                            replant.setAge(0);
                            block.setBlockData(replant, true);
                            harvested++;
                        }
                    }
                }
            }
        } finally {
            plugin.guards().areaBreaking(player.getUniqueId(), false);
            ctx.put("harvested", String.valueOf(harvested));
        }
    }

    /** Extends the clicked block along the clicked face, using blocks from the inventory (survival). */
    private void buildLine(ActivationContext ctx, String[] p) {
        Block origin = ctx.block();
        Player player = ctx.player();
        if (origin == null) {
            return;
        }
        BlockFace face;
        try {
            face = BlockFace.valueOf(ctx.format("%face%"));
        } catch (IllegalArgumentException ex) {
            face = player.getFacing();
        }
        Material material = origin.getType();
        if (!material.isSolid() || !material.isItem() || origin.getState() instanceof org.bukkit.block.Container) {
            return;
        }
        int length = (int) Math.max(1, Math.min(32, Expr.num(p[0], 8)));
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int placed = 0;
        Block previous = origin;
        for (int i = 1; i <= length; i++) {
            Block target = origin.getRelative(face, i);
            if (!target.getType().isAir() && !target.isReplaceable()) {
                break;
            }
            if (!creative && !player.getInventory().containsAtLeast(new ItemStack(material), 1)) {
                plugin.engine().message(ctx, "no-blocks", "block", Text.pretty(material.name()));
                break;
            }
            BlockState replaced = target.getState();
            target.setType(material, false);
            BlockPlaceEvent event = new BlockPlaceEvent(target, replaced, previous, new ItemStack(material), player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !event.canBuild()) {
                replaced.update(true, false);
                break;
            }
            if (!creative) {
                player.getInventory().removeItem(new ItemStack(material, 1));
            }
            previous = target;
            placed++;
        }
        if (placed > 0) {
            player.playSound(origin.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 1.2f);
        }
        ctx.put("placed", String.valueOf(placed));
    }

    // ───────────────────────── loot ─────────────────────────

    private void loot(Action action, ActivationContext ctx) {
        List<Action.LootEntry> entries = action.loot();
        double total = 0;
        for (Action.LootEntry e : entries) {
            total += e.weight();
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        Action.LootEntry chosen = entries.get(entries.size() - 1);
        for (Action.LootEntry e : entries) {
            roll -= e.weight();
            if (roll < 0) {
                chosen = e;
                break;
            }
        }
        ctx.put("chance", Expr.format(chosen.weight() * 100 / total));
        for (Action sub : chosen.actions()) {
            runner.execute(sub, ctx);
        }
    }
}
