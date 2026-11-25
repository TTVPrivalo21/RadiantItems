package dev.privalo.items.action;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.activator.ActivationContext;
import dev.privalo.items.item.Attributes;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.util.Expr;
import dev.privalo.items.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Executes parsed actions sequentially; {@code DELAY: ticks} schedules the rest. */
public final class ActionRunner {

    private static final Map<String, String> EFFECT_ALIASES = Map.ofEntries(
            Map.entry("slow", "slowness"), Map.entry("fast_digging", "haste"), Map.entry("slow_digging", "mining_fatigue"),
            Map.entry("increase_damage", "strength"), Map.entry("heal", "instant_health"), Map.entry("harm", "instant_damage"),
            Map.entry("jump", "jump_boost"), Map.entry("confusion", "nausea"), Map.entry("damage_resistance", "resistance"));
    private static final Map<String, String> PARTICLE_ALIASES = Map.ofEntries(
            Map.entry("REDSTONE", "DUST"), Map.entry("SPELL_WITCH", "WITCH"), Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
            Map.entry("FIREWORKS_SPARK", "FIREWORK"), Map.entry("SPELL", "EFFECT"), Map.entry("SPELL_INSTANT", "INSTANT_EFFECT"),
            Map.entry("CRIT_MAGIC", "ENCHANTED_HIT"), Map.entry("SMOKE_NORMAL", "SMOKE"), Map.entry("SMOKE_LARGE", "LARGE_SMOKE"),
            Map.entry("EXPLOSION_LARGE", "EXPLOSION"), Map.entry("EXPLOSION_HUGE", "EXPLOSION_EMITTER"),
            Map.entry("DRIP_LAVA", "DRIPPING_LAVA"), Map.entry("WATER_SPLASH", "SPLASH"), Map.entry("TOTEM", "TOTEM_OF_UNDYING"),
            Map.entry("ENCHANTMENT_TABLE", "ENCHANT"), Map.entry("PORTAL", "PORTAL"), Map.entry("SNOW_SHOVEL", "ITEM_SNOWBALL"));

    private final RadiantItems plugin;
    private Map<String, String> soundKeys;
    private final ExtraActions extra;

    public ActionRunner(RadiantItems plugin) {
        this.plugin = plugin;
        this.extra = new ExtraActions(plugin, this);
    }

    public void run(List<Action> actions, ActivationContext ctx) {
        run(actions, 0, ctx);
    }

    private void run(List<Action> actions, int start, ActivationContext ctx) {
        for (int i = start; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (!ctx.player().isOnline() || !plugin.isEnabled()) {
                return;
            }
            if (action.type() == Action.Type.DELAY) {
                long ticks = Math.max(1, (long) Expr.num(ctx.format(action.args()), 20));
                int next = i + 1;
                Bukkit.getScheduler().runTaskLater(plugin, () -> run(actions, next, ctx), ticks);
                return;
            }
            try {
                execute(action, ctx);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[" + ctx.item().id + "] la acción '" + action.raw() + "' falló: " + ex.getMessage());
            }
        }
    }

    // ───────────────────────── targets ─────────────────────────

    List<Entity> entities(Action action, ActivationContext ctx) {
        Player player = ctx.player();
        return switch (action.target()) {
            case SELF -> List.of(player);
            case TARGET -> ctx.target() == null || !ctx.target().isValid() ? List.of() : List.of(ctx.target());
            case NEARBY -> {
                double r = action.radius();
                List<Entity> out = new ArrayList<>();
                for (Entity e : player.getNearbyEntities(r, r, r)) {
                    if (e instanceof LivingEntity && e != player && e.getLocation().distanceSquared(player.getLocation()) <= r * r) {
                        out.add(e);
                    }
                }
                yield out;
            }
            case ONLINE -> new ArrayList<>(Bukkit.getOnlinePlayers());
            case BLOCK -> List.of();
        };
    }

    List<Location> locations(Action action, ActivationContext ctx) {
        if (action.target() == Action.Target.BLOCK) {
            return List.of(ctx.blockOrLook(60));
        }
        List<Location> out = new ArrayList<>();
        for (Entity e : entities(action, ctx)) {
            out.add(e.getLocation());
        }
        return out;
    }

    private static Location center(Entity e) {
        return e.getLocation().add(0, e.getHeight() / 2, 0);
    }

    // ───────────────────────── dispatch ─────────────────────────

    void execute(Action action, ActivationContext ctx) {
        Player player = ctx.player();
        String args = ctx.format(action.args());
        String[] p = args.isBlank() ? new String[0] : args.trim().split("\\s+");
        switch (action.type()) {
            case CONSOLE -> forEachTarget(action, ctx, cmd -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), strip(cmd)));
            case PLAYER -> forEachTarget(action, ctx, cmd -> player.performCommand(strip(cmd)));
            case MESSAGE -> {
                for (Entity e : entities(action, ctx)) {
                    if (e instanceof Player target) {
                        target.sendMessage(Text.parse(ctx.format(action.args(), ctx.target())));
                    }
                }
            }
            case BROADCAST -> Bukkit.broadcast(Text.parse(args));
            case TITLE -> {
                String[] parts = args.split("\\|");
                long in = parts.length > 2 ? (long) Expr.num(parts[2].trim().split("\\s+")[0], 10) : 10;
                long stay = parts.length > 2 && parts[2].trim().split("\\s+").length > 1 ? (long) Expr.num(parts[2].trim().split("\\s+")[1], 40) : 40;
                long out = parts.length > 2 && parts[2].trim().split("\\s+").length > 2 ? (long) Expr.num(parts[2].trim().split("\\s+")[2], 10) : 10;
                Title title = Title.title(Text.parse(parts[0].trim()), Text.parse(parts.length > 1 ? parts[1].trim() : ""),
                        Title.Times.times(Duration.ofMillis(in * 50), Duration.ofMillis(stay * 50), Duration.ofMillis(out * 50)));
                players(action, ctx).forEach(t -> t.showTitle(title));
            }
            case ACTIONBAR -> players(action, ctx).forEach(t -> t.sendActionBar(Text.parse(args)));
            case BOSSBAR -> bossBar(action, ctx, args);
            case SOUND -> {
                String key = soundKey(p[0]);
                float volume = p.length > 1 ? (float) Expr.num(p[1], 1) : 1f;
                float pitch = p.length > 2 ? (float) Expr.num(p[2], 1) : 1f;
                for (Location loc : locations(action, ctx)) {
                    loc.getWorld().playSound(loc, key, SoundCategory.PLAYERS, volume, pitch);
                }
            }
            case PARTICLE -> {
                int count = p.length > 1 ? (int) Expr.num(p[1], 10) : 10;
                double spread = p.length > 2 ? Expr.num(p[2], 0.3) : 0.3;
                double speed = p.length > 3 ? Expr.num(p[3], 0.02) : 0.02;
                String color = p.length > 4 ? p[4] : null;
                float size = p.length > 5 ? (float) Expr.num(p[5], 1) : 1f;
                if (action.target() == Action.Target.BLOCK) {
                    particle(ctx.blockOrLook(60), p[0], count, spread, speed, color, size);
                } else {
                    for (Entity e : entities(action, ctx)) {
                        particle(center(e), p[0], count, spread, speed, color, size);
                    }
                }
            }
            case PARTICLE_CIRCLE -> {
                double radius = p.length > 1 ? Expr.num(p[1], 1) : 1;
                int points = p.length > 2 ? (int) Expr.num(p[2], 24) : 24;
                String color = p.length > 3 ? p[3] : null;
                for (Location base : locations(action, ctx)) {
                    for (int i = 0; i < points; i++) {
                        double angle = 2 * Math.PI * i / points;
                        particle(base.clone().add(Math.cos(angle) * radius, 0.1, Math.sin(angle) * radius), p[0], 1, 0, 0, color, 1f);
                    }
                }
            }
            case PARTICLE_SPIRAL -> {
                double radius = p.length > 1 ? Expr.num(p[1], 0.8) : 0.8;
                double height = p.length > 2 ? Expr.num(p[2], 2) : 2;
                String color = p.length > 3 ? p[3] : null;
                for (Location base : locations(action, ctx)) {
                    int steps = (int) Math.max(12, height * 24);
                    for (int i = 0; i < steps; i++) {
                        double t = (double) i / steps;
                        double angle = t * Math.PI * 6;
                        particle(base.clone().add(Math.cos(angle) * radius, t * height, Math.sin(angle) * radius), p[0], 1, 0, 0, color, 1f);
                    }
                }
            }
            case PARTICLE_LINE -> {
                double length = p.length > 1 ? Expr.num(p[1], 10) : 10;
                double step = p.length > 2 ? Math.max(0.1, Expr.num(p[2], 0.4)) : 0.4;
                String color = p.length > 3 ? p[3] : null;
                Location from = player.getEyeLocation().subtract(0, 0.2, 0);
                List<Location> ends = new ArrayList<>();
                if (action.target() == Action.Target.SELF) {
                    ends.add(from.clone().add(from.getDirection().multiply(length)));
                } else if (action.target() == Action.Target.BLOCK) {
                    ends.add(ctx.blockOrLook(length));
                } else {
                    entities(action, ctx).forEach(e -> ends.add(center(e)));
                }
                for (Location to : ends) {
                    Vector dir = to.toVector().subtract(from.toVector());
                    double dist = dir.length();
                    if (dist < 0.01) {
                        continue;
                    }
                    dir.normalize();
                    for (double d = 0; d <= dist; d += step) {
                        particle(from.clone().add(dir.clone().multiply(d)), p[0], 1, 0, 0, color, 1f);
                    }
                }
            }
            case EFFECT -> {
                PotionEffectType type = effect(p[0]);
                int duration = p.length > 1 ? (int) Expr.num(p[1], 100) : 100;
                int amplifier = p.length > 2 ? (int) Expr.num(p[2], 0) : 0;
                boolean hide = p.length > 3 && p[3].equalsIgnoreCase("hide");
                PotionEffect effect = new PotionEffect(type, duration, amplifier, false, !hide, true);
                living(action, ctx).forEach(le -> le.addPotionEffect(effect));
            }
            case REMOVE_EFFECT -> living(action, ctx).forEach(le -> {
                if (p[0].equalsIgnoreCase("ALL")) {
                    new ArrayList<>(le.getActivePotionEffects()).forEach(e -> le.removePotionEffect(e.getType()));
                } else {
                    le.removePotionEffect(effect(p[0]));
                }
            });
            case HEAL -> {
                double amount = Expr.num(args, 0);
                for (LivingEntity le : living(action, ctx)) {
                    Attribute max = Attributes.resolve("max_health");
                    AttributeInstance inst = max == null ? null : le.getAttribute(max);
                    double cap = inst == null ? 20 : inst.getValue();
                    le.setHealth(Math.max(0, Math.min(cap, le.getHealth() + amount)));
                }
            }
            case FEED -> {
                int food = (int) Expr.num(p[0], 0);
                float saturation = p.length > 1 ? (float) Expr.num(p[1], 0) : 0f;
                for (Player t : players(action, ctx)) {
                    t.setFoodLevel(Math.max(0, Math.min(20, t.getFoodLevel() + food)));
                    t.setSaturation(Math.max(0, Math.min(t.getFoodLevel(), t.getSaturation() + saturation)));
                }
            }
            case DAMAGE -> {
                double amount = Expr.num(args, 0);
                plugin.guards().actionDamage(player.getUniqueId(), true);
                try {
                    for (LivingEntity le : living(action, ctx)) {
                        if (le == player) {
                            le.damage(amount);
                        } else {
                            le.damage(amount, player);
                        }
                    }
                } finally {
                    plugin.guards().actionDamage(player.getUniqueId(), false);
                }
            }
            case IGNITE -> {
                int ticks = (int) Expr.num(args, 60);
                entities(action, ctx).forEach(e -> e.setFireTicks(Math.max(e.getFireTicks(), ticks)));
            }
            case EXTINGUISH -> entities(action, ctx).forEach(e -> e.setFireTicks(0));
            case VELOCITY -> velocity(action, ctx, p);
            case GRAPPLE -> grapple(ctx, p);
            case NO_FALL -> players(action, ctx).forEach(t -> plugin.guards().noFall(t.getUniqueId(), (long) Expr.num(args, 60)));
            case PROJECTILE -> projectile(ctx, p);
            case LIGHTNING -> {
                boolean effectOnly = p.length > 0 && p[0].equalsIgnoreCase("effect");
                for (Location loc : locations(action, ctx)) {
                    if (effectOnly) {
                        loc.getWorld().strikeLightningEffect(loc);
                    } else {
                        loc.getWorld().strikeLightning(loc);
                    }
                }
            }
            case BLINK -> players(action, ctx).forEach(t -> blink(t, Expr.num(args, 8)));
            case EXPLOSION -> {
                float power = p.length > 0 ? (float) Expr.num(p[0], 2) : 2f;
                boolean fire = p.length > 1 && Boolean.parseBoolean(p[1]);
                boolean selfDamage = p.length > 2 && Boolean.parseBoolean(p[2]);
                if (!selfDamage) {
                    plugin.guards().safeExplosion(player.getUniqueId(), true);
                }
                try {
                    for (Location loc : locations(action, ctx)) {
                        loc.getWorld().createExplosion(loc, Math.min(power, 10f), fire, false, player);
                    }
                } finally {
                    plugin.guards().safeExplosion(player.getUniqueId(), false);
                }
            }
            case TELEPORT -> teleport(action, ctx, p);
            case MINE_AREA -> mineArea(ctx, p);
            case GIVE_ITEM -> players(action, ctx).forEach(t -> give(t, p));
            case TAKE_ITEM -> players(action, ctx).forEach(t -> take(t, p));
            case GIVE_XP -> players(action, ctx).forEach(t -> xp(t, p, 1));
            case TAKE_XP -> players(action, ctx).forEach(t -> xp(t, p, -1));
            case MONEY -> {
                double amount = Expr.num(args, 0);
                if (!plugin.hooks().economy()) {
                    plugin.engine().message(ctx, "no-economy");
                } else {
                    for (Player t : players(action, ctx)) {
                        if (amount >= 0) {
                            plugin.hooks().deposit(t, amount);
                        } else {
                            plugin.hooks().withdraw(t, -amount);
                        }
                    }
                }
            }
            case VARIABLE -> variable(ctx, args);
            case MAGNET, DETECT, PLAYER_TIME, PLAYER_WEATHER, LAUNCH_ENTITY, FIREWORK, HOMING, VEIN_MINE, HARVEST,
                 BUILD_LINE, REPAIR, LOOT, OPEN, WEAR -> extra.execute(action, ctx, args, p);
            case USES -> uses(ctx, args);
            case DELAY -> {
                // handled by run()
            }
        }
    }

    private void forEachTarget(Action action, ActivationContext ctx, java.util.function.Consumer<String> run) {
        if (action.target() == Action.Target.NEARBY || action.target() == Action.Target.ONLINE || action.target() == Action.Target.TARGET) {
            for (Entity e : entities(action, ctx)) {
                run.accept(ctx.format(action.args(), e));
            }
        } else {
            run.accept(ctx.format(action.args()));
        }
    }

    private static String strip(String command) {
        String c = command.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }

    List<Player> players(Action action, ActivationContext ctx) {
        List<Player> out = new ArrayList<>();
        for (Entity e : entities(action, ctx)) {
            if (e instanceof Player t) {
                out.add(t);
            }
        }
        if (action.target() == Action.Target.BLOCK) {
            out.add(ctx.player());
        }
        return out;
    }

    List<LivingEntity> living(Action action, ActivationContext ctx) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : entities(action, ctx)) {
            if (e instanceof LivingEntity le && !le.isDead()) {
                out.add(le);
            }
        }
        return out;
    }

    // ───────────────────────── visuals ─────────────────────────

    private void bossBar(Action action, ActivationContext ctx, String args) {
        String[] parts = args.split("\\|");
        BossBar.Color color = BossBar.Color.PURPLE;
        if (parts.length > 1) {
            try {
                color = BossBar.Color.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                color = BossBar.Color.PURPLE;
            }
        }
        double seconds = parts.length > 2 ? Expr.num(parts[2].trim(), 3) : 3;
        float progress = parts.length > 3 ? (float) Math.max(0, Math.min(1, Expr.num(parts[3].trim(), 1))) : 1f;
        BossBar bar = BossBar.bossBar(Text.parse(parts[0].trim()), progress, color, BossBar.Overlay.PROGRESS);
        List<Player> viewers = players(action, ctx);
        viewers.forEach(v -> v.showBossBar(bar));
        Bukkit.getScheduler().runTaskLater(plugin, () -> viewers.forEach(v -> v.hideBossBar(bar)), Math.max(1, Math.round(seconds * 20)));
    }

    public void particle(Location loc, String name, int count, double spread, double speed, String colorRaw, float size) {
        Particle particle = particle(name);
        World world = loc.getWorld();
        if (particle == null || world == null) {
            return;
        }
        Color color = Text.color(colorRaw, Color.WHITE);
        Class<?> type = particle.getDataType();
        Object data = null;
        if (type == Particle.DustOptions.class) {
            data = new Particle.DustOptions(color, Math.max(0.1f, size));
        } else if (type == Particle.DustTransition.class) {
            data = new Particle.DustTransition(color, Color.WHITE, Math.max(0.1f, size));
        } else if (type == Color.class) {
            data = color;
        } else if (type == BlockData.class) {
            Material m = colorRaw == null ? null : Material.matchMaterial(colorRaw);
            data = (m != null && m.isBlock() ? m : Material.STONE).createBlockData();
        } else if (type == ItemStack.class) {
            Material m = colorRaw == null ? null : Material.matchMaterial(colorRaw);
            data = new ItemStack(m != null && m.isItem() ? m : Material.STONE);
        } else if (type == Float.class) {
            data = 0f;
        } else if (type == Integer.class) {
            data = 0;
        } else if (type != Void.class) {
            return;
        }
        world.spawnParticle(particle, loc, Math.max(1, count), spread, spread, spread, speed, data);
    }

    private final Map<String, Particle> particleCache = new HashMap<>();

    /** Canonical particle enum name (legacy aliases resolved); also used by the offline validator. */
    public static String particleName(String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        return PARTICLE_ALIASES.getOrDefault(key, key);
    }

    /** Canonical potion effect key (legacy aliases resolved); also used by the offline validator. */
    public static String effectKey(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        return EFFECT_ALIASES.getOrDefault(key, key);
    }

    private Particle particle(String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        return particleCache.computeIfAbsent(key, k -> {
            String name = PARTICLE_ALIASES.getOrDefault(k, k);
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Partícula desconocida: " + raw);
                return null;
            }
        });
    }

    private String soundKey(String raw) {
        String clean = raw.trim();
        if (clean.contains(".") || clean.contains(":")) {
            return clean.toLowerCase(Locale.ROOT);
        }
        if (soundKeys == null) {
            Map<String, String> map = new HashMap<>();
            try {
                for (Sound sound : Registry.SOUNDS) {
                    NamespacedKey key = Registry.SOUNDS.getKey(sound);
                    if (key == null) {
                        continue;
                    }
                    map.put(key.getKey().toUpperCase(Locale.ROOT).replace('.', '_'), key.toString());
                }
            } catch (Throwable unsupported) {
                plugin.getLogger().fine("No se pudo indexar el registro de sonidos: " + unsupported.getMessage());
            }
            soundKeys = map;
        }
        String found = soundKeys.get(clean.toUpperCase(Locale.ROOT));
        return found != null ? found : clean.toLowerCase(Locale.ROOT).replace('_', '.');
    }

    private static PotionEffectType effect(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        key = EFFECT_ALIASES.getOrDefault(key, key);
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (type == null) {
            throw new IllegalArgumentException("efecto desconocido " + raw);
        }
        return type;
    }

    // ───────────────────────── movement ─────────────────────────

    private void velocity(Action action, ActivationContext ctx, String[] p) {
        Player player = ctx.player();
        boolean absolute = p.length >= 4 && p[3].equalsIgnoreCase("absolute") || p.length == 3;
        for (Entity e : entities(action, ctx)) {
            Vector v;
            if (absolute) {
                v = new Vector(Expr.num(p[0], 0), Expr.num(p[1], 0), Expr.num(p[2], 0));
            } else {
                double forward = Expr.num(p[0], 1);
                double up = p.length > 1 ? Expr.num(p[1], 0) : 0;
                Vector dir = e == player
                        ? player.getEyeLocation().getDirection()
                        : e.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (dir.lengthSquared() < 1e-6) {
                    dir = player.getEyeLocation().getDirection();
                }
                v = dir.normalize().multiply(forward).add(new Vector(0, up, 0));
            }
            if (v.length() > 6) {
                v.normalize().multiply(6);
            }
            e.setVelocity(v);
            if (e instanceof Player t) {
                t.setFallDistance(0);
                plugin.guards().noFall(t.getUniqueId(), 40);
            }
        }
    }

    private void grapple(ActivationContext ctx, String[] p) {
        Player player = ctx.player();
        double range = p.length > 0 ? Expr.num(p[0], 30) : 30;
        double strength = p.length > 1 ? Expr.num(p[1], 1.6) : 1.6;
        Location eye = player.getEyeLocation();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) {
            plugin.engine().message(ctx, "grapple-miss");
            return;
        }
        Vector to = hit.getHitPosition();
        Vector delta = to.clone().subtract(player.getLocation().toVector());
        double distance = delta.length();
        Vector v = delta.normalize().multiply(Math.min(4.0, strength * (0.6 + distance / 12.0)));
        v.setY(Math.max(v.getY(), 0) + 0.35);
        player.setVelocity(v);
        player.setFallDistance(0);
        plugin.guards().noFall(player.getUniqueId(), 100);
        double step = 0.5;
        Vector dir = to.clone().subtract(eye.toVector()).normalize();
        double length = to.distance(eye.toVector());
        for (double d = 0; d < length; d += step) {
            player.getWorld().spawnParticle(Particle.CRIT, eye.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0);
        }
    }

    private void blink(Player player, double distance) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(eye, dir, distance, FluidCollisionMode.NEVER, true);
        double travel = hit == null ? distance : Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 0.8);
        Location dest = player.getLocation().add(dir.clone().multiply(travel));
        while (travel > 0 && !(passable(dest.getBlock()) && passable(dest.clone().add(0, 1, 0).getBlock()))) {
            travel -= 0.5;
            dest = player.getLocation().add(dir.clone().multiply(Math.max(0, travel)));
        }
        if (travel <= 0) {
            return;
        }
        player.setFallDistance(0);
        player.teleport(dest);
    }

    private static boolean passable(Block block) {
        return block.isPassable() && !block.isLiquid();
    }

    private void teleport(Action action, ActivationContext ctx, String[] p) {
        for (Player t : players(action, ctx)) {
            Location dest;
            String mode = p[0].toLowerCase(Locale.ROOT);
            if (mode.equals("spawn")) {
                dest = t.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
            } else if (mode.equals("bed") || mode.equals("home")) {
                dest = t.getRespawnLocation();
                if (dest == null) {
                    plugin.engine().message(ctx, "no-bed");
                    dest = t.getWorld().getSpawnLocation().add(0.5, 0, 0.5);
                }
            } else if (mode.equals("target") && ctx.target() != null) {
                dest = ctx.target().getLocation();
            } else {
                int offset = 0;
                World world = t.getWorld();
                if (p.length >= 4 && Bukkit.getWorld(p[0]) != null) {
                    world = Bukkit.getWorld(p[0]);
                    offset = 1;
                }
                Location base = t.getLocation();
                double x = coord(p[offset], base.getX());
                double y = coord(p[offset + 1], base.getY());
                double z = coord(p[offset + 2], base.getZ());
                float yaw = p.length > offset + 3 ? (float) Expr.num(p[offset + 3], base.getYaw()) : base.getYaw();
                float pitch = p.length > offset + 4 ? (float) Expr.num(p[offset + 4], base.getPitch()) : base.getPitch();
                dest = new Location(world, x, y, z, yaw, pitch);
            }
            t.setFallDistance(0);
            t.teleport(dest);
        }
    }

    private static double coord(String raw, double base) {
        if (raw.startsWith("~")) {
            return base + (raw.length() > 1 ? Expr.num(raw.substring(1), 0) : 0);
        }
        return Expr.num(raw, base);
    }

    private void projectile(ActivationContext ctx, String[] p) {
        Player player = ctx.player();
        String type = p[0].toUpperCase(Locale.ROOT);
        double speed = p.length > 1 ? Expr.num(p[1], 1.5) : 1.5;
        boolean safe = p.length <= 2 || !p[2].equalsIgnoreCase("unsafe");
        Class<? extends Projectile> cls = switch (type) {
            case "FIREBALL", "METEOR" -> Fireball.class;
            case "SMALL_FIREBALL" -> SmallFireball.class;
            case "ARROW" -> Arrow.class;
            case "SPECTRAL_ARROW" -> SpectralArrow.class;
            case "SNOWBALL" -> Snowball.class;
            case "EGG" -> Egg.class;
            case "TRIDENT" -> Trident.class;
            case "WITHER_SKULL" -> WitherSkull.class;
            case "DRAGON_FIREBALL" -> DragonFireball.class;
            case "WIND_CHARGE" -> WindCharge.class;
            case "ENDER_PEARL", "PEARL" -> org.bukkit.entity.EnderPearl.class;
            default -> throw new IllegalArgumentException("proyectil desconocido " + type);
        };
        Vector velocity = player.getEyeLocation().getDirection().multiply(speed);
        Projectile projectile = player.launchProjectile(cls, velocity);
        projectile.setVelocity(velocity);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        if (projectile instanceof Fireball fireball) {
            fireball.setIsIncendiary(!safe);
        }
        tagProjectile(projectile, ctx.item(), ctx.ref() == null ? null : ctx.ref().uuid(), safe);
    }

    public static void tagProjectile(Projectile projectile, CustomItem def, UUID itemUuid, boolean safe) {
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        pdc.set(ItemData.PROJECTILE_ITEM, PersistentDataType.STRING, def.id);
        if (itemUuid != null) {
            pdc.set(ItemData.PROJECTILE_UUID, PersistentDataType.STRING, itemUuid.toString());
        }
        if (safe) {
            pdc.set(ItemData.SAFE, PersistentDataType.BYTE, (byte) 1);
        }
    }

    // ───────────────────────── blocks ─────────────────────────

    private void mineArea(ActivationContext ctx, String[] p) {
        Block origin = ctx.block();
        Player player = ctx.player();
        if (origin == null) {
            return;
        }
        int radius = p.length > 0 ? (int) Math.max(0, Math.min(3, Expr.num(p[0], 1))) : 1;
        int depth = p.length > 1 ? (int) Math.max(0, Math.min(3, Expr.num(p[1], 0))) : 0;
        boolean preferredOnly = p.length <= 2 || Boolean.parseBoolean(p[2]);
        BlockFace face = player.getTargetBlockFace(8);
        if (face == null || face == BlockFace.SELF) {
            float pitch = player.getLocation().getPitch();
            face = pitch > 50 ? BlockFace.UP : pitch < -50 ? BlockFace.DOWN : player.getFacing().getOppositeFace();
        }
        double maxHardness = plugin.getConfig().getDouble("mine-area.max-hardness", 50);
        List<String> blacklist = plugin.getConfig().getStringList("mine-area.blacklist");
        ItemStack tool = player.getInventory().getItemInMainHand();
        Vector normal = face.getDirection();
        List<Block> targets = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                for (int d = 0; d <= depth; d++) {
                    int dx;
                    int dy;
                    int dz;
                    if (face == BlockFace.UP || face == BlockFace.DOWN) {
                        dx = a;
                        dz = b;
                        dy = 0;
                    } else if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                        dx = a;
                        dy = b;
                        dz = 0;
                    } else {
                        dz = a;
                        dy = b;
                        dx = 0;
                    }
                    dx -= (int) Math.round(normal.getX()) * d;
                    dy -= (int) Math.round(normal.getY()) * d;
                    dz -= (int) Math.round(normal.getZ()) * d;
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    targets.add(origin.getRelative(dx, dy, dz));
                }
            }
        }
        int broken = 1;
        plugin.guards().areaBreaking(player.getUniqueId(), true);
        try {
            for (Block block : targets) {
                Material type = block.getType();
                if (type.isAir() || block.isLiquid() || blacklist.contains(type.name())) {
                    continue;
                }
                float hardness = type.getHardness();
                if (hardness < 0 || hardness > maxHardness) {
                    continue;
                }
                if (preferredOnly && !block.isPreferredTool(tool)) {
                    continue;
                }
                if (player.getInventory().getItemInMainHand().getType().isAir()) {
                    break;
                }
                if (player.breakBlock(block)) {
                    broken++;
                }
            }
        } finally {
            plugin.guards().areaBreaking(player.getUniqueId(), false);
            ctx.put("broken", String.valueOf(broken));
        }
    }

    // ───────────────────────── items / xp ─────────────────────────

    private void give(Player player, String[] p) {
        int amount = p.length > 1 ? (int) Math.max(1, Expr.num(p[1], 1)) : 1;
        List<ItemStack> stacks = new ArrayList<>();
        if (p[0].toLowerCase(Locale.ROOT).startsWith("ri:")) {
            CustomItem def = plugin.items().get(p[0].substring(3));
            if (def == null) {
                throw new IllegalArgumentException("ítem desconocido " + p[0]);
            }
            stacks.addAll(plugin.giveStacks(def, amount));
        } else {
            Material material = Material.matchMaterial(p[0]);
            if (material == null || !material.isItem()) {
                throw new IllegalArgumentException("material desconocido " + p[0]);
            }
            int left = amount;
            while (left > 0) {
                int n = Math.min(left, material.getMaxStackSize());
                stacks.add(new ItemStack(material, n));
                left -= n;
            }
        }
        Collection<ItemStack> overflow = player.getInventory().addItem(stacks.toArray(new ItemStack[0])).values();
        overflow.forEach(s -> player.getWorld().dropItemNaturally(player.getLocation(), s));
    }

    private void take(Player player, String[] p) {
        int left = p.length > 1 ? (int) Math.max(1, Expr.num(p[1], 1)) : 1;
        boolean custom = p[0].toLowerCase(Locale.ROOT).startsWith("ri:");
        String id = custom ? p[0].substring(3).toLowerCase(Locale.ROOT) : null;
        Material material = custom ? null : Material.matchMaterial(p[0]);
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            String stackId = ItemData.id(stack);
            boolean match = custom ? id.equals(stackId) : stack.getType() == material && stackId == null;
            if (!match) {
                continue;
            }
            int n = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - n);
            left -= n;
            inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
        }
    }

    private static void xp(Player player, String[] p, int sign) {
        int amount = (int) Expr.num(p[0], 0) * sign;
        boolean levels = p.length > 1 && (p[1].equalsIgnoreCase("L") || p[1].equalsIgnoreCase("levels"));
        if (levels) {
            player.giveExpLevels(amount);
        } else if (amount >= 0) {
            player.giveExp(amount);
        } else {
            int total = Math.max(0, totalXp(player) + amount);
            player.setExp(0);
            player.setLevel(0);
            player.setTotalExperience(0);
            player.giveExp(total);
        }
    }

    private static int totalXp(Player player) {
        int level = player.getLevel();
        int base;
        if (level <= 16) {
            base = level * level + 6 * level;
        } else if (level <= 31) {
            base = (int) (2.5 * level * level - 40.5 * level + 360);
        } else {
            base = (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        return base + Math.round(player.getExp() * player.getExpToLevel());
    }

    // ───────────────────────── item state ─────────────────────────

    private void variable(ActivationContext ctx, String args) {
        String[] parts = args.trim().split("\\s+", 3);
        String name = ItemData.cleanVar(parts[0]);
        String op = parts[1].replace("=", "").isEmpty() ? "=" : parts[1].substring(0, 1);
        String value = parts[2];
        editItem(ctx, meta -> {
            Map<String, String> vars = ItemData.effectiveVars(ctx.item(), meta);
            String current = vars.getOrDefault(name, "0");
            String result;
            if (op.equals("=")) {
                result = Expr.isNumeric(value) ? Expr.format(Expr.eval(value)) : value;
            } else {
                double a = Expr.num(current, 0);
                double b = Expr.num(value, 0);
                double r = switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    default -> b == 0 ? a : a / b;
                };
                result = Expr.format(r);
            }
            vars.put(name, result);
            ItemData.vars(meta.getPersistentDataContainer(), vars);
        });
    }

    private void uses(ActivationContext ctx, String args) {
        CustomItem def = ctx.item();
        if (def.maxUses <= 0) {
            return;
        }
        String raw = args.trim();
        editItem(ctx, meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            int current = ItemData.uses(pdc, def.maxUses);
            int next;
            if (raw.startsWith("+")) {
                next = current + (int) Expr.num(raw.substring(1), 0);
            } else if (raw.startsWith("-")) {
                next = current - (int) Expr.num(raw.substring(1), 0);
            } else if (raw.startsWith("=")) {
                next = (int) Expr.num(raw.substring(1), current);
            } else if (raw.equalsIgnoreCase("max") || raw.equalsIgnoreCase("full")) {
                next = def.maxUses;
            } else {
                next = (int) Expr.num(raw, current);
            }
            ItemData.setUses(pdc, Math.max(0, Math.min(def.maxUses, next)));
        });
    }

    private void editItem(ActivationContext ctx, java.util.function.Consumer<ItemMeta> edit) {
        if (ctx.ref() == null) {
            return;
        }
        ItemStack stack = ctx.ref().get();
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        edit.accept(meta);
        plugin.factory().render(ctx.item(), meta, ctx.player());
        stack.setItemMeta(meta);
        ctx.ref().set(stack);
    }
}
