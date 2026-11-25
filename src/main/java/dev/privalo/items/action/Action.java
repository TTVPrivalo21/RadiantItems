package dev.privalo.items.action;

import dev.privalo.items.util.Expr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * One parsed action line: {@code [@self|@target|@nearby:R|@block|@online] TYPE: args}.
 * Arguments keep their placeholders and are formatted at execution time.
 */
public final class Action {

    public enum Target { SELF, TARGET, NEARBY, BLOCK, ONLINE }

    public enum Type {
        DELAY, CONSOLE, PLAYER, MESSAGE, BROADCAST, TITLE, ACTIONBAR, BOSSBAR, SOUND,
        PARTICLE, PARTICLE_CIRCLE, PARTICLE_LINE, PARTICLE_SPIRAL,
        EFFECT, REMOVE_EFFECT, HEAL, FEED, DAMAGE, IGNITE, EXTINGUISH, VELOCITY, GRAPPLE, NO_FALL,
        PROJECTILE, LIGHTNING, BLINK, EXPLOSION, TELEPORT, MINE_AREA,
        GIVE_ITEM, TAKE_ITEM, GIVE_XP, TAKE_XP, MONEY, VARIABLE, USES,
        MAGNET, DETECT, PLAYER_TIME, PLAYER_WEATHER, LAUNCH_ENTITY, FIREWORK, HOMING, VEIN_MINE, HARVEST,
        BUILD_LINE, REPAIR, LOOT, OPEN, WEAR
    }

    /** One weighted outcome of a LOOT action: "peso=ACCIÓN && ACCIÓN". */
    public record LootEntry(double weight, List<Action> actions) {
    }

    public static final List<String> OPEN_TYPES = List.of(
            "workbench", "enderchest", "enchanting", "anvil", "grindstone", "stonecutter", "loom", "smithing", "cartography");

    private final Target target;
    private final double radius;
    private final Type type;
    private final String args;
    private final String raw;
    private final List<LootEntry> loot;

    private Action(Target target, double radius, Type type, String args, String raw, List<LootEntry> loot) {
        this.loot = loot;
        this.target = target;
        this.radius = radius;
        this.type = type;
        this.args = args;
        this.raw = raw;
    }

    public Target target() {
        return target;
    }

    public double radius() {
        return radius;
    }

    public Type type() {
        return type;
    }

    public String args() {
        return args;
    }

    public String raw() {
        return raw;
    }

    public List<LootEntry> loot() {
        return loot;
    }

    public static Action parse(String raw) {
        String line = raw.trim();
        Target target = null;
        double radius = 0;
        if (line.startsWith("@")) {
            int end = line.indexOf(' ');
            if (end < 0) {
                throw new IllegalArgumentException("Falta la acción tras el selector: " + raw);
            }
            String selector = line.substring(1, end).toLowerCase(Locale.ROOT);
            line = line.substring(end + 1).trim();
            if (selector.startsWith("nearby")) {
                target = Target.NEARBY;
                radius = selector.contains(":") ? Expr.num(selector.substring(selector.indexOf(':') + 1), 5) : 5;
                if (radius <= 0 || radius > 64) {
                    throw new IllegalArgumentException("@nearby admite un radio de 0 a 64: " + raw);
                }
            } else {
                target = switch (selector) {
                    case "self", "player", "me" -> Target.SELF;
                    case "target", "victim" -> Target.TARGET;
                    case "block", "look" -> Target.BLOCK;
                    case "online", "all" -> Target.ONLINE;
                    default -> throw new IllegalArgumentException("Selector desconocido @" + selector + " (usa @self, @target, @nearby:R, @block, @online)");
                };
            }
        }
        String name;
        String args;
        int colon = line.indexOf(':');
        int space = line.indexOf(' ');
        if (colon >= 0 && (space < 0 || colon < space)) {
            name = line.substring(0, colon).trim();
            args = line.substring(colon + 1).trim();
        } else if (space >= 0) {
            name = line.substring(0, space).trim();
            args = line.substring(space + 1).trim();
        } else {
            name = line;
            args = "";
        }
        Type type = type(name);
        if (target == null) {
            target = switch (type) {
                case LIGHTNING -> Target.BLOCK;
                default -> Target.SELF;
            };
        }
        validate(type, args, raw);
        List<LootEntry> loot = type == Type.LOOT ? parseLoot(args, raw) : List.of();
        return new Action(target, radius, type, args, raw.trim(), loot);
    }

    private static Type type(String name) {
        String key = name.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "WAIT" -> Type.DELAY;
            case "CMD", "COMMAND", "CONSOLE_COMMAND" -> Type.CONSOLE;
            case "PLAYER_COMMAND" -> Type.PLAYER;
            case "MSG", "TELL" -> Type.MESSAGE;
            case "ACTION_BAR" -> Type.ACTIONBAR;
            case "BOSS_BAR" -> Type.BOSSBAR;
            case "POTION", "POTION_EFFECT" -> Type.EFFECT;
            case "LAUNCH", "SHOOT", "FIREBALL" -> Type.PROJECTILE;
            case "PUSH", "IMPULSE", "LEAP" -> Type.VELOCITY;
            case "FIRE", "SET_FIRE" -> Type.IGNITE;
            case "TP", "TELEPORT_TO" -> Type.TELEPORT;
            case "EXPLODE" -> Type.EXPLOSION;
            case "GIVE" -> Type.GIVE_ITEM;
            case "TAKE" -> Type.TAKE_ITEM;
            case "XP", "EXP" -> Type.GIVE_XP;
            case "VAR", "SET_VARIABLE" -> Type.VARIABLE;
            case "MINE", "AREA_BREAK", "BREAK_AREA" -> Type.MINE_AREA;
            case "VEIN", "VEINMINE", "TREE_CAPITATOR" -> Type.VEIN_MINE;
            case "RANDOM", "REWARD", "LOTTERY" -> Type.LOOT;
            case "SPAWN_ENTITY", "THROW_ENTITY" -> Type.LAUNCH_ENTITY;
            case "EQUIP_HEAD", "HAT" -> Type.WEAR;
            default -> {
                try {
                    yield Type.valueOf(key);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Acción desconocida '" + name + "'. Válidas: " + Arrays.toString(Type.values()));
                }
            }
        };
    }

    /** "60=GIVE_ITEM: DIAMOND 1 && MESSAGE: hi ;; 40=..." → weighted entries (nested LOOT/DELAY not allowed). */
    private static List<LootEntry> parseLoot(String args, String raw) {
        List<LootEntry> out = new ArrayList<>();
        for (String part : args.split(";;")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int eq = entry.indexOf('=');
            if (eq <= 0 || !Expr.isNumeric(entry.substring(0, eq))) {
                throw new IllegalArgumentException("Cada opción de LOOT empieza por \"peso=\" (ej. 60=GIVE_ITEM: DIAMOND 1): " + entry);
            }
            double weight = Expr.eval(entry.substring(0, eq));
            if (weight <= 0) {
                throw new IllegalArgumentException("Peso de LOOT debe ser > 0: " + entry);
            }
            List<Action> actions = new ArrayList<>();
            for (String sub : entry.substring(eq + 1).split("&&")) {
                if (sub.isBlank()) {
                    continue;
                }
                Action action = parse(sub.trim());
                if (action.type() == Type.LOOT || action.type() == Type.DELAY) {
                    throw new IllegalArgumentException("LOOT no admite " + action.type() + " dentro: " + sub.trim());
                }
                actions.add(action);
            }
            if (actions.isEmpty()) {
                throw new IllegalArgumentException("Opción de LOOT sin acciones: " + entry);
            }
            out.add(new LootEntry(weight, List.copyOf(actions)));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("LOOT sin opciones: " + raw);
        }
        return List.copyOf(out);
    }

    private static void validate(Type type, String args, String raw) {
        boolean empty = args.isBlank();
        switch (type) {
            case DELAY, HEAL, FEED, DAMAGE, IGNITE, NO_FALL, BLINK, GIVE_XP, TAKE_XP, MONEY -> {
                if (empty) {
                    throw new IllegalArgumentException(type + " necesita un número: " + raw);
                }
                // these read the whole argument as one expression; FEED / XP read only the first token
                boolean whole = type != Type.FEED && type != Type.GIVE_XP && type != Type.TAKE_XP;
                String number = whole ? args.trim() : args.trim().split("\\s+")[0];
                String probe = number.replaceAll("%[A-Za-z0-9_.:-]+%", "1");
                if (!Expr.isNumeric(probe)) {
                    throw new IllegalArgumentException(type + " necesita un número o expresión, no '" + number + "': " + raw);
                }
            }
            case CONSOLE, PLAYER, MESSAGE, BROADCAST, TITLE, ACTIONBAR, BOSSBAR, SOUND, PARTICLE,
                 PARTICLE_CIRCLE, PARTICLE_LINE, PARTICLE_SPIRAL, EFFECT, REMOVE_EFFECT, PROJECTILE,
                 TELEPORT, GIVE_ITEM, TAKE_ITEM, VELOCITY, USES -> {
                if (empty) {
                    throw new IllegalArgumentException(type + " necesita argumentos: " + raw);
                }
            }
            case DETECT -> {
                String mode = empty ? "" : args.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                if (!mode.equals("ores") && !mode.equals("entities")) {
                    throw new IllegalArgumentException("DETECT usa \"ores <radio>\" o \"entities <radio>\": " + raw);
                }
            }
            case OPEN -> {
                if (empty || !OPEN_TYPES.contains(args.trim().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("OPEN admite " + OPEN_TYPES + ": " + raw);
                }
            }
            case PLAYER_WEATHER -> {
                if (empty || !List.of("clear", "rain", "reset").contains(args.trim().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("PLAYER_WEATHER admite clear, rain o reset: " + raw);
                }
            }
            case PLAYER_TIME, LAUNCH_ENTITY, FIREWORK, MAGNET, HOMING, VEIN_MINE, HARVEST, BUILD_LINE, REPAIR, LOOT -> {
                if (empty) {
                    throw new IllegalArgumentException(type + " necesita argumentos: " + raw);
                }
            }
            case VARIABLE -> {
                String[] p = args.trim().split("\\s+", 3);
                if (p.length < 3) {
                    throw new IllegalArgumentException("VARIABLE usa \"nombre operador valor\" (ej. kills + 1): " + raw);
                }
                if (!p[1].matches("=|\\+=?|-=?|\\*=?|/=?")) {
                    throw new IllegalArgumentException("Operador de VARIABLE inválido '" + p[1] + "' (usa = + - * /): " + raw);
                }
            }
            default -> {
                // EXTINGUISH, LIGHTNING, EXPLOSION, GRAPPLE, MINE_AREA accept optional arguments
            }
        }
    }
}
