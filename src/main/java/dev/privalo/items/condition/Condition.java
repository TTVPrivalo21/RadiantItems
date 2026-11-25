package dev.privalo.items.condition;

import dev.privalo.items.activator.ActivationContext;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.util.Expr;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One requirement of an activator. Syntax: {@code "TYPE: value"}, {@code "!TYPE"} (negated)
 * or a map {@code {check: "TYPE: value", message: "..."}}.
 * MONEY / ITEM / XP_COST are costs: checked first, charged only when every check passed.
 */
public final class Condition {

    public enum Type {
        PERMISSION(true), WORLD(true), MIN_HEALTH(true), MAX_HEALTH(true), MIN_FOOD(true), MAX_FOOD(true),
        MIN_LEVEL(true), MAX_LEVEL(true), SNEAKING(false), SPRINTING(false), FLYING(false), GLIDING(false),
        ON_GROUND(false), IN_WATER(false), GAMEMODE(true), TIME(true), PLACEHOLDER(true), VARIABLE(true),
        USES(true), TARGET_TYPE(true), BLOCKING(false), BLOCK_TYPE(true), MONEY(true), ITEM(true), XP_COST(true);

        private final boolean needsValue;

        Type(boolean needsValue) {
            this.needsValue = needsValue;
        }

        public boolean needsValue() {
            return needsValue;
        }

        public boolean cost() {
            return this == MONEY || this == ITEM || this == XP_COST;
        }
    }

    private final Type type;
    private final String value;
    private final boolean negate;
    private final String message;
    private final String raw;

    private Condition(Type type, String value, boolean negate, String message, String raw) {
        this.type = type;
        this.value = value;
        this.negate = negate;
        this.message = message;
        this.raw = raw;
    }

    public Type type() {
        return type;
    }

    public String value() {
        return value;
    }

    public boolean negated() {
        return negate;
    }

    public String message() {
        return message;
    }

    public String raw() {
        return raw;
    }

    /** True for MONEY / ITEM / XP_COST that are not negated. */
    public boolean chargeable() {
        return type.cost() && !negate;
    }

    // ───────────────────────── parsing ─────────────────────────

    public static Condition parse(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Object check = map.get("check");
            if (check == null) {
                check = map.get("condition");
            }
            if (check == null) {
                throw new IllegalArgumentException("La condición en formato mapa necesita 'check:'");
            }
            Object msg = map.get("message");
            return parse(String.valueOf(check), msg == null ? null : String.valueOf(msg));
        }
        String text = String.valueOf(entry);
        int bar = text.indexOf(" | ");
        return bar < 0 ? parse(text, null) : parse(text.substring(0, bar), text.substring(bar + 3).trim());
    }

    public static Condition parse(String raw, String message) {
        String line = raw.trim();
        boolean negate = false;
        if (line.startsWith("!")) {
            negate = true;
            line = line.substring(1).trim();
        }
        String name;
        String value;
        int colon = line.indexOf(':');
        int space = line.indexOf(' ');
        if (colon >= 0 && (space < 0 || colon < space)) {
            name = line.substring(0, colon).trim();
            value = line.substring(colon + 1).trim();
        } else if (space >= 0) {
            name = line.substring(0, space).trim();
            value = line.substring(space + 1).trim();
        } else {
            name = line;
            value = "";
        }
        name = name.toUpperCase(Locale.ROOT).replace('-', '_');
        switch (name) {
            case "NOT_WORLD", "NO_WORLD" -> {
                name = "WORLD";
                negate = !negate;
            }
            case "NOT_SNEAKING" -> {
                name = "SNEAKING";
                negate = !negate;
            }
            case "PERM" -> name = "PERMISSION";
            case "WORLDS" -> name = "WORLD";
            case "LEVEL", "MIN_XP_LEVEL" -> name = "MIN_LEVEL";
            case "COST", "MONEY_COST" -> name = "MONEY";
            case "ITEM_COST" -> name = "ITEM";
            case "LEVEL_COST" -> name = "XP_COST";
            case "PAPI" -> name = "PLACEHOLDER";
            case "VAR" -> name = "VARIABLE";
            case "SWIMMING" -> name = "IN_WATER";
            case "TARGET" -> name = "TARGET_TYPE";
            case "BLOCK", "BLOCKS" -> name = "BLOCK_TYPE";
            case "SHIELD", "IS_BLOCKING" -> name = "BLOCKING";
            default -> {
                // already canonical
            }
        }
        Type type;
        try {
            type = Type.valueOf(name);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Condición desconocida '" + name + "'. Válidas: " + Arrays.toString(Type.values()));
        }
        if (type.needsValue() && value.isEmpty()) {
            throw new IllegalArgumentException("La condición " + type + " necesita un valor (ej. \"" + type + ": ...\")");
        }
        validate(type, value);
        return new Condition(type, value, negate, message, raw.trim());
    }

    private static void validate(Type type, String value) {
        switch (type) {
            case MIN_HEALTH, MAX_HEALTH, MIN_FOOD, MAX_FOOD, MIN_LEVEL, MAX_LEVEL, MONEY, XP_COST -> {
                if (!value.contains("%") && !Expr.isNumeric(value)) {
                    throw new IllegalArgumentException(type + " necesita un número, no '" + value + "'");
                }
            }
            case VARIABLE, PLACEHOLDER, USES -> {
                if (type != Type.USES) {
                    Expr.requireComparison(value);
                }
            }
            case GAMEMODE -> {
                for (String mode : value.split(",")) {
                    GameMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
                }
            }
            case TIME -> {
                String v = value.toUpperCase(Locale.ROOT);
                if (!v.equals("DAY") && !v.equals("NIGHT") && !v.contains("-")) {
                    throw new IllegalArgumentException("TIME acepta DAY, NIGHT o un rango de ticks como 0-12000");
                }
            }
            case ITEM -> {
                String first = value.split("\\s+")[0];
                if (!first.toLowerCase(Locale.ROOT).startsWith("ri:") && Material.matchMaterial(first) == null) {
                    throw new IllegalArgumentException("Material desconocido en ITEM: " + first);
                }
            }
            default -> {
                // free-form values (permission node, world list, entity list)
            }
        }
    }

    // ───────────────────────── evaluation ─────────────────────────

    public boolean test(ActivationContext ctx) {
        return check(ctx) != negate;
    }

    private boolean check(ActivationContext ctx) {
        Player p = ctx.player();
        String v = ctx.format(value);
        return switch (type) {
            case PERMISSION -> p.hasPermission(v);
            case WORLD -> list(v).stream().anyMatch(w -> w.equalsIgnoreCase(p.getWorld().getName()));
            case MIN_HEALTH -> p.getHealth() >= Expr.num(v, 0);
            case MAX_HEALTH -> p.getHealth() <= Expr.num(v, Double.MAX_VALUE);
            case MIN_FOOD -> p.getFoodLevel() >= Expr.num(v, 0);
            case MAX_FOOD -> p.getFoodLevel() <= Expr.num(v, 20);
            case MIN_LEVEL -> p.getLevel() >= Expr.num(v, 0);
            case MAX_LEVEL -> p.getLevel() <= Expr.num(v, Integer.MAX_VALUE);
            case SNEAKING -> p.isSneaking();
            case SPRINTING -> p.isSprinting();
            case FLYING -> p.isFlying();
            case GLIDING -> p.isGliding();
            case ON_GROUND -> isOnGround(p);
            case IN_WATER -> p.isInWater();
            case GAMEMODE -> list(v).stream().anyMatch(m -> m.equalsIgnoreCase(p.getGameMode().name()));
            case TIME -> time(p.getWorld().getTime(), v);
            case PLACEHOLDER, VARIABLE -> Expr.compare(v);
            case USES -> uses(ctx, v);
            case TARGET_TYPE -> target(ctx.target(), v);
            case BLOCKING -> p.isBlocking();
            case BLOCK_TYPE -> ctx.block() != null && blockMatches(ctx.block().getType().name(), v);
            case MONEY -> ctx.plugin().hooks().economy() && ctx.plugin().hooks().hasMoney(p, Expr.num(v, 0));
            case ITEM -> count(ctx, v) >= amount(v);
            case XP_COST -> p.getLevel() >= Expr.num(v, 0);
        };
    }

    @SuppressWarnings("deprecation")
    private static boolean isOnGround(Player p) {
        Block below = p.getLocation().subtract(0, 0.1, 0).getBlock();
        return p.isOnGround() || below.getType().isSolid();
    }

    private static boolean time(long ticks, String value) {
        String v = value.toUpperCase(Locale.ROOT);
        if (v.equals("DAY")) {
            return ticks < 12300 || ticks > 23850;
        }
        if (v.equals("NIGHT")) {
            return ticks >= 12300 && ticks <= 23850;
        }
        String[] range = v.split("-");
        long from = (long) Expr.num(range[0], 0);
        long to = (long) Expr.num(range[1], 24000);
        return from <= to ? ticks >= from && ticks <= to : ticks >= from || ticks <= to;
    }

    private static boolean uses(ActivationContext ctx, String v) {
        CustomItem def = ctx.item();
        if (def.maxUses <= 0) {
            return true;
        }
        String expr = v.trim();
        if (Expr.isNumeric(expr)) {
            expr = ">= " + expr;
        }
        return Expr.compare(ctx.uses() + " " + expr);
    }

    private static boolean target(Entity target, String v) {
        if (target == null) {
            return false;
        }
        for (String t : list(v)) {
            String key = t.toUpperCase(Locale.ROOT);
            boolean match = switch (key) {
                case "PLAYER", "PLAYERS" -> target instanceof Player;
                case "MOB", "MOBS" -> !(target instanceof Player) && target instanceof org.bukkit.entity.Mob;
                case "MONSTER", "MONSTERS", "HOSTILE" -> target instanceof Monster;
                case "ANIMAL", "ANIMALS" -> target instanceof Animals;
                default -> target.getType().name().equals(key);
            };
            if (match) {
                return true;
            }
        }
        return false;
    }

    /** "STONE,*_ORE,OAK_*" style matching (wildcard * at either end). */
    public static boolean blockMatches(String type, String patterns) {
        for (String raw : list(patterns)) {
            String p = raw.toUpperCase(Locale.ROOT);
            boolean match;
            if (p.startsWith("*") && p.endsWith("*") && p.length() > 1) {
                match = type.contains(p.substring(1, p.length() - 1));
            } else if (p.startsWith("*")) {
                match = type.endsWith(p.substring(1));
            } else if (p.endsWith("*")) {
                match = type.startsWith(p.substring(0, p.length() - 1));
            } else {
                match = type.equals(p);
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private static List<String> list(String v) {
        return Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static int amount(String v) {
        String[] parts = v.trim().split("\\s+");
        return parts.length > 1 ? (int) Math.max(1, Expr.num(parts[1], 1)) : 1;
    }

    private static boolean matches(ActivationContext ctx, ItemStack stack, String what) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (what.toLowerCase(Locale.ROOT).startsWith("ri:")) {
            return what.substring(3).equalsIgnoreCase(ItemData.id(stack));
        }
        Material material = Material.matchMaterial(what);
        return material != null && stack.getType() == material && ItemData.id(stack) == null;
    }

    private static int count(ActivationContext ctx, String v) {
        String what = v.trim().split("\\s+")[0];
        int total = 0;
        for (ItemStack stack : ctx.player().getInventory().getStorageContents()) {
            if (matches(ctx, stack, what)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Takes the cost. Only called after every condition of the activator passed. */
    public void charge(ActivationContext ctx) {
        if (!chargeable()) {
            return;
        }
        Player p = ctx.player();
        String v = ctx.format(value);
        switch (type) {
            case MONEY -> ctx.plugin().hooks().withdraw(p, Expr.num(v, 0));
            case XP_COST -> p.setLevel(Math.max(0, p.getLevel() - (int) Expr.num(v, 0)));
            case ITEM -> {
                String what = v.trim().split("\\s+")[0];
                int left = amount(v);
                PlayerInventory inv = p.getInventory();
                ItemStack[] contents = inv.getStorageContents();
                for (int i = 0; i < contents.length && left > 0; i++) {
                    ItemStack stack = contents[i];
                    if (!matches(ctx, stack, what)) {
                        continue;
                    }
                    int take = Math.min(left, stack.getAmount());
                    stack.setAmount(stack.getAmount() - take);
                    left -= take;
                    inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
                }
            }
            default -> {
                // not a cost
            }
        }
    }

}
