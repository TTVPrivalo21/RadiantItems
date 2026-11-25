package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.action.Action;
import dev.privalo.items.activator.Activator;
import dev.privalo.items.condition.Condition;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.util.Expr;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Edits one activator: trigger, filters, cooldown, chance, consumption, conditions, actions, messages. */
public final class ActivatorMenu extends ItemMenu {

    static final List<String> ACTION_HINT = List.of(
            "Selector opcional: @self @target @nearby:5 @block @online",
            "DELAY: 20 · CONSOLE: say hi · PLAYER: spawn",
            "MESSAGE/ACTIONBAR/BROADCAST: texto",
            "TITLE: título | subtítulo | 10 40 10",
            "BOSSBAR: texto | RED | segundos | progreso",
            "SOUND: entity.blaze.shoot 1 1",
            "PARTICLE: FLAME 20 0.3 0.02 [#color] [tamaño]",
            "PARTICLE_CIRCLE / PARTICLE_LINE / PARTICLE_SPIRAL",
            "EFFECT: SPEED 100 1 · REMOVE_EFFECT: ALL",
            "HEAL: 4 · FEED: 6 · DAMAGE: 5 · IGNITE: 60",
            "VELOCITY: 1.5 0.4 · GRAPPLE: 30 1.6 · BLINK: 8",
            "PROJECTILE: FIREBALL 1.5 · LIGHTNING [effect]",
            "EXPLOSION: 2 · TELEPORT: spawn|bed|x y z",
            "MINE_AREA: 1 0 · GIVE_ITEM: DIAMOND 2 | ri:id",
            "GIVE_XP: 10 [L] · MONEY: 50 · USES: +5",
            "VARIABLE: kills + 1",
            "MAGNET: 8 · DETECT: ores 10 | entities 30",
            "PLAYER_TIME: day · PLAYER_WEATHER: clear",
            "LAUNCH_ENTITY: SHEEP 1.5 100 · FIREWORK: BALL #FB923C 1",
            "@target HOMING: 16 60 (con SHOOT_BOW)",
            "VEIN_MINE: 48 · HARVEST: 3 · BUILD_LINE: 8",
            "REPAIR: 100 offhand|armor|all · OPEN: workbench",
            "WEAR · LOOT: 60=GIVE_ITEM: IRON_INGOT 4 ;; 40=...");
    static final List<String> CONDITION_HINT = List.of(
            "Prefijo ! para negar. Mensaje propio: 'COND | mensaje'",
            "PERMISSION: nodo · WORLD: world,world_nether",
            "MIN_HEALTH/MAX_HEALTH/MIN_FOOD/MAX_FOOD: n",
            "MIN_LEVEL/MAX_LEVEL: n · GAMEMODE: SURVIVAL",
            "SNEAKING · SPRINTING · FLYING · GLIDING",
            "ON_GROUND · IN_WATER · TIME: DAY|NIGHT|0-12000",
            "PLACEHOLDER: %player_level% >= 10",
            "VARIABLE: %kills% >= 50 · USES: 3",
            "TARGET_TYPE: PLAYER,ZOMBIE,MONSTER",
            "BLOCKING · BLOCK_TYPE: *_ORE,*_LOG,CHEST",
            "Costes: MONEY: 100 · ITEM: DIAMOND 2 · XP_COST: 5");

    private String key;
    private final Menu back;

    public ActivatorMenu(RadiantItems plugin, Player viewer, String itemId, String key, Menu back) {
        super(plugin, viewer, itemId);
        this.key = key;
        this.back = back;
    }

    private Activator act() {
        CustomItem d = def();
        return d == null ? null : d.activator(key);
    }

    @Override
    public void open() {
        if (def() != null && act() == null) {
            back.open();
            return;
        }
        super.open();
    }

    @Override
    protected String title() {
        return "<dark_gray>Activador · <gold>" + key;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        Activator a = act();
        if (a == null) {
            return;
        }
        button(4, icon(a.type.icon(), "<gold>" + a.key, "<gray>" + a.type.description(), "",
                "<gray>Clic: <white>renombrar el activador</white>"), e -> ask("Nuevo nombre del activador (a-z 0-9 _):", text -> {
            String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
            if (clean.isEmpty() || def().activator(clean) != null) {
                throw new IllegalArgumentException("Nombre vacío o ya usado: " + clean);
            }
            act().key = clean;
            key = clean;
            save();
        }));

        button(10, icon(a.type.icon(), "<gold>Tipo", "<gray>Actual: <white>" + a.type.name(), "<gray>Clic para cambiar"),
                e -> new TypeMenu(plugin, viewer, itemId, key, this).open());
        button(11, icon(Material.CLOCK, "<gold>Cooldown", "<gray>Actual: <white>" + Expr.format(a.cooldown) + "s", "",
                "<gray>Izq: <white>+1s</white> · Der: <white>-1s</white>", "<gray>Shift: <white>±0.5s</white> · Central/Q: <white>escribir</white>"),
                e -> number(e, 1, 0.5, "Cooldown en segundos:", v -> act().cooldown = Math.max(0, v), act().cooldown));
        button(12, icon(Material.PLAYER_HEAD, "<gold>Alcance del cooldown", "<gray>Actual: <white>" + a.scope,
                "<dark_gray>PLAYER: por jugador", "<dark_gray>GLOBAL: compartido por todo el servidor", "<dark_gray>ITEM: por copia (UUID) del ítem",
                "", "<gray>Clic para alternar"), e -> {
            Activator.CooldownScope[] v = Activator.CooldownScope.values();
            act().scope = v[(act().scope.ordinal() + 1) % v.length];
            save();
        });
        button(13, icon(Material.RABBIT_FOOT, "<gold>Probabilidad", "<gray>Actual: <white>" + Expr.format(a.chance) + "%", "",
                "<gray>Izq: <white>+5</white> · Der: <white>-5</white>", "<gray>Shift: <white>±1</white> · Central/Q: <white>escribir</white>"),
                e -> number(e, 5, 1, "Probabilidad (0-100):", v -> act().chance = Math.max(0, Math.min(100, v)), act().chance));
        button(14, icon(Material.EXPERIENCE_BOTTLE, "<gold>Consume usos", "<gray>Actual: <white>" + a.consumeUsage, "",
                "<gray>Izq: <white>+1</white> · Der: <white>-1</white>", "<dark_gray>Requiere max-uses > 0 en el ítem."),
                e -> number(e, 1, 5, "Usos que consume:", v -> act().consumeUsage = (int) Math.max(0, v), act().consumeUsage));
        toggle(15, Material.BOWL, "Consume el ítem", a.consumeItem, x -> x.consumeItem = !x.consumeItem);
        button(16, icon(Material.STRUCTURE_VOID, "<gold>Cancelar evento vanilla",
                "<gray>Actual: <white>" + (a.cancelEvent == null ? "auto (" + (a.type.cancelByDefault() ? "sí" : "no") + ")" : a.cancelEvent ? "sí" : "no"),
                "<gray>Clic: alternar auto → sí → no"), e -> {
            Activator x = act();
            x.cancelEvent = x.cancelEvent == null ? Boolean.TRUE : x.cancelEvent ? Boolean.FALSE : null;
            save();
        });

        if (a.type.click()) {
            button(19, icon(Material.GRASS_BLOCK, "<gold>Aire / bloque", "<gray>Actual: <white>" + a.click, "<gray>Clic para alternar"), e -> {
                Activator.ClickMode[] v = Activator.ClickMode.values();
                act().click = v[(act().click.ordinal() + 1) % v.length];
                save();
            });
        }
        if (a.type.slotted()) {
            button(20, icon(Material.ARMOR_STAND, "<gold>Ranura", "<gray>Actual: <white>" + a.slot,
                    "<dark_gray>MAIN_HAND, OFF_HAND, HAND, ARMOR, ANY", "<gray>Clic para alternar"), e -> {
                Activator.SlotMode[] v = Activator.SlotMode.values();
                act().slot = v[(act().slot.ordinal() + 1) % v.length];
                save();
            });
        }
        if (a.type == dev.privalo.items.activator.ActivatorType.HOLD_LOOP) {
            button(21, icon(Material.REPEATER, "<gold>Intervalo", "<gray>Actual: <white>" + a.interval + " ticks", "",
                    "<gray>Izq: <white>+5</white> · Der: <white>-5</white>", "<gray>Shift: <white>±20</white>"),
                    e -> number(e, 5, 20, "Intervalo en ticks (20 = 1s):", v -> act().interval = (int) Math.max(1, v), act().interval));
        }
        button(22, icon(Material.ZOMBIE_HEAD, "<gold>Filtro de entidades", "<gray>Actual: <white>" + a.entities,
                "<dark_gray>Para HIT_ENTITY / KILL_ENTITY / PROJECTILE_HIT", "<gray>Clic para alternar"), e -> {
            Activator.EntityFilter[] v = Activator.EntityFilter.values();
            act().entities = v[(act().entities.ordinal() + 1) % v.length];
            save();
        });
        toggle(23, Material.BELL, "Avisar del cooldown", a.cooldownMessage, x -> x.cooldownMessage = !x.cooldownMessage);

        // ── conditions / actions / messages ──
        List<String> condLore = new ArrayList<>();
        a.conditions.forEach(c -> condLore.add("<dark_gray>• <white>" + escape(Activator.conditionText(c))));
        if (condLore.isEmpty()) {
            condLore.add("<gray>Ninguna.");
        }
        condLore.add("");
        condLore.add("<gray>Clic para editar.");
        button(29, icon(Material.TRIPWIRE_HOOK, "<gold>Condiciones <gray>(" + a.conditions.size() + ")", condLore),
                e -> new ListMenu(plugin, viewer, itemId, "<dark_gray>Condiciones · <gold>" + key, new ListMenu.Source() {
                    @Override
                    public List<String> get(CustomItem def) {
                        Activator x = def.activator(key);
                        List<String> out = new ArrayList<>();
                        if (x != null) {
                            x.conditions.forEach(c -> out.add(Activator.conditionText(c)));
                        }
                        return out;
                    }

                    @Override
                    public void set(CustomItem def, List<String> lines) {
                        Activator x = def.activator(key);
                        if (x != null) {
                            x.conditions.clear();
                            lines.forEach(l -> x.conditions.add(Activator.conditionEntry(l)));
                            x.invalidate();
                        }
                    }
                }, ActivatorMenu::validateCondition, CONDITION_HINT, this).open());

        List<String> actLore = new ArrayList<>();
        a.actions.forEach(c -> actLore.add("<dark_gray>• <white>" + escape(c)));
        if (actLore.isEmpty()) {
            actLore.add("<gray>Ninguna.");
        }
        actLore.add("");
        actLore.add("<gray>Clic para editar.");
        button(31, glow(icon(Material.COMMAND_BLOCK, "<gold>Acciones <gray>(" + a.actions.size() + ")", actLore), true),
                e -> new ListMenu(plugin, viewer, itemId, "<dark_gray>Acciones · <gold>" + key, new ListMenu.Source() {
                    @Override
                    public List<String> get(CustomItem def) {
                        Activator x = def.activator(key);
                        return x == null ? new ArrayList<>() : x.actions;
                    }

                    @Override
                    public void set(CustomItem def, List<String> lines) {
                        Activator x = def.activator(key);
                        if (x != null) {
                            x.actions.clear();
                            x.actions.addAll(lines);
                            x.invalidate();
                        }
                    }
                }, ActivatorMenu::validateAction, ACTION_HINT, this).open());

        List<String> msgLore = new ArrayList<>();
        a.messages.forEach((k, v) -> msgLore.add("<dark_gray>• <yellow>" + k + "</yellow>: <white>" + escape(v)));
        if (msgLore.isEmpty()) {
            msgLore.add("<gray>Usando los mensajes de config.yml.");
        }
        msgLore.add("");
        msgLore.add("<gray>Claves: success, cooldown, no-uses, not-owner,");
        msgLore.add("<gray>item-broke, chance-failed, mode (chat|actionbar)");
        button(33, icon(Material.OAK_SIGN, "<gold>Mensajes", msgLore),
                e -> new ListMenu(plugin, viewer, itemId, "<dark_gray>Mensajes · <gold>" + key, new ListMenu.Source() {
                    @Override
                    public List<String> get(CustomItem def) {
                        Activator x = def.activator(key);
                        List<String> out = new ArrayList<>();
                        if (x != null) {
                            x.messages.forEach((k, v) -> out.add(k + ": " + v));
                        }
                        return out;
                    }

                    @Override
                    public void set(CustomItem def, List<String> lines) {
                        Activator x = def.activator(key);
                        if (x == null) {
                            return;
                        }
                        Map<String, String> map = new LinkedHashMap<>();
                        for (String line : lines) {
                            int colon = line.indexOf(':');
                            map.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                        }
                        x.messages.clear();
                        x.messages.putAll(map);
                    }
                }, line -> line.indexOf(':') > 0 ? null : "Formato: clave: texto (ej. success: <green>¡Hecho!)",
                        List.of("Formato: clave: texto", "success: <green>¡Activado!", "cooldown: <red>Espera %time%s", "mode: actionbar"), this).open());

        button(45, icon(Material.ARROW, "<yellow>Volver"), e -> back.open());
        button(53, icon(Material.LAVA_BUCKET, "<red>Eliminar activador", "<gray>Shift+clic derecho"), e -> {
            if (e.getClick() == ClickType.SHIFT_RIGHT) {
                def().activators.removeIf(x -> x.key.equals(key));
                save();
                back.open();
            }
        });
    }

    private void toggle(int slot, Material material, String name, boolean value, Consumer<Activator> flip) {
        button(slot, glow(icon(material, "<gold>" + name, "<gray>Activo: " + onOff(value), "<gray>Clic para alternar"), value), e -> {
            flip.accept(act());
            save();
        });
    }

    private void number(InventoryClickEvent e, double step, double shiftStep, String prompt, java.util.function.DoubleConsumer apply, double current) {
        double delta = switch (e.getClick()) {
            case LEFT -> step;
            case RIGHT -> -step;
            case SHIFT_LEFT -> shiftStep;
            case SHIFT_RIGHT -> -shiftStep;
            default -> Double.NaN;
        };
        if (Double.isNaN(delta)) {
            ask(prompt, text -> {
                if (!Expr.isNumeric(text)) {
                    throw new IllegalArgumentException("'" + text + "' no es un número");
                }
                apply.accept(Expr.eval(text));
                save();
            });
            return;
        }
        apply.accept(current + delta);
        save();
    }

    static String validateAction(String line) {
        try {
            Action.parse(line);
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    static String validateCondition(String line) {
        try {
            Condition.parse(Activator.conditionEntry(line));
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }
}
