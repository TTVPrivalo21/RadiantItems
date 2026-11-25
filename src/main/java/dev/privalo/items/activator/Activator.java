package dev.privalo.items.activator;

import dev.privalo.items.action.Action;
import dev.privalo.items.condition.Condition;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One activator of an item: trigger + filters + cooldown/chance/consumption + conditions + actions. */
public final class Activator {

    public enum ClickMode { BOTH, AIR, BLOCK }

    public enum SlotMode { MAIN_HAND, OFF_HAND, HAND, ARMOR, ANY }

    public enum CooldownScope { PLAYER, GLOBAL, ITEM }

    public enum EntityFilter { ANY, PLAYER, MOB }

    public String key;
    public ActivatorType type = ActivatorType.RIGHT_CLICK;
    public ClickMode click = ClickMode.BOTH;
    public SlotMode slot = SlotMode.MAIN_HAND;
    public int interval = 20;
    public double cooldown;
    public CooldownScope scope = CooldownScope.PLAYER;
    public boolean cooldownMessage = true;
    public double chance = 100;
    public int consumeUsage;
    public boolean consumeItem;
    public Boolean cancelEvent;
    public EntityFilter entities = EntityFilter.ANY;
    public final List<Object> conditions = new ArrayList<>();
    public final List<String> actions = new ArrayList<>();
    public final Map<String, String> messages = new LinkedHashMap<>();

    private List<Condition> parsedConditions;
    private List<Action> parsedActions;

    public Activator(String key) {
        this.key = key;
    }

    public boolean cancels() {
        return cancelEvent != null ? cancelEvent : type.cancelByDefault();
    }

    /** Parses conditions/actions; invalid lines are reported through {@code errors} and skipped. */
    public void compile(List<String> errors) {
        List<Condition> conds = new ArrayList<>();
        for (Object entry : conditions) {
            try {
                conds.add(Condition.parse(entry));
            } catch (IllegalArgumentException ex) {
                errors.add("activador '" + key + "', condición: " + ex.getMessage());
            }
        }
        List<Action> acts = new ArrayList<>();
        for (String line : actions) {
            try {
                acts.add(Action.parse(line));
            } catch (IllegalArgumentException ex) {
                errors.add("activador '" + key + "', acción: " + ex.getMessage());
            }
        }
        parsedConditions = List.copyOf(conds);
        parsedActions = List.copyOf(acts);
    }

    public List<Condition> parsedConditions() {
        if (parsedConditions == null) {
            compile(new ArrayList<>());
        }
        return parsedConditions;
    }

    public List<Action> parsedActions() {
        if (parsedActions == null) {
            compile(new ArrayList<>());
        }
        return parsedActions;
    }

    /** Call after editing conditions/actions in the GUI. */
    public void invalidate() {
        parsedConditions = null;
        parsedActions = null;
    }

    /** Text form of a condition entry (strings stay as-is, maps become "check | message"). */
    public static String conditionText(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Object check = map.get("check");
            Object message = map.get("message");
            return check + (message == null ? "" : " | " + message);
        }
        return String.valueOf(entry);
    }

    /** Inverse of {@link #conditionText(Object)}: "check | message" → map, plain text → string. */
    public static Object conditionEntry(String text) {
        int bar = text.indexOf(" | ");
        if (bar < 0) {
            return text.trim();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("check", text.substring(0, bar).trim());
        map.put("message", text.substring(bar + 3).trim());
        return map;
    }

    // ───────────────────────── YAML ─────────────────────────

    public static Activator read(String key, ConfigurationSection s) {
        Activator a = new Activator(key);
        a.type = ActivatorType.parse(s.getString("type", "RIGHT_CLICK"));
        a.click = parseEnum(ClickMode.class, s.getString("click", "BOTH"), ClickMode.BOTH);
        a.slot = parseEnum(SlotMode.class, s.getString("slot", a.type.loop() ? "HAND" : a.type.slotted() ? "ANY" : "MAIN_HAND"), SlotMode.MAIN_HAND);
        a.interval = Math.max(1, s.getInt("interval", 20));
        a.cooldown = Math.max(0, s.getDouble("cooldown", 0));
        a.scope = parseEnum(CooldownScope.class, s.getString("cooldown-scope", "PLAYER"), CooldownScope.PLAYER);
        a.cooldownMessage = s.getBoolean("cooldown-message", true);
        a.chance = Math.max(0, Math.min(100, s.getDouble("chance", 100)));
        a.consumeUsage = Math.max(0, s.getInt("consume-usage", 0));
        a.consumeItem = s.getBoolean("consume-item", false);
        a.cancelEvent = s.isSet("cancel-event") ? s.getBoolean("cancel-event") : null;
        a.entities = parseEnum(EntityFilter.class, s.getString("entities", "ANY"), EntityFilter.ANY);
        List<?> conds = s.getList("conditions");
        if (conds != null) {
            for (Object entry : conds) {
                a.conditions.add(entry instanceof Map<?, ?> ? normalize((Map<?, ?>) entry) : String.valueOf(entry));
            }
        }
        a.actions.addAll(s.getStringList("actions"));
        ConfigurationSection msgs = s.getConfigurationSection("messages");
        if (msgs != null) {
            for (String k : msgs.getKeys(false)) {
                a.messages.put(k, msgs.getString(k, ""));
            }
        }
        return a;
    }

    private static Map<String, Object> normalize(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> out.put(String.valueOf(k), String.valueOf(v)));
        return out;
    }

    public void write(ConfigurationSection s) {
        s.set("type", type.name());
        if (type.click()) {
            s.set("click", click.name());
        }
        if (type.slotted()) {
            s.set("slot", slot.name());
        }
        if (type == ActivatorType.HOLD_LOOP) {
            s.set("interval", interval);
        }
        if (cooldown > 0) {
            s.set("cooldown", cooldown);
            s.set("cooldown-scope", scope.name());
            if (!cooldownMessage) {
                s.set("cooldown-message", false);
            }
        }
        if (chance < 100) {
            s.set("chance", chance);
        }
        if (consumeUsage > 0) {
            s.set("consume-usage", consumeUsage);
        }
        if (consumeItem) {
            s.set("consume-item", true);
        }
        if (cancelEvent != null) {
            s.set("cancel-event", cancelEvent);
        }
        if (entities != EntityFilter.ANY) {
            s.set("entities", entities.name());
        }
        s.set("conditions", new ArrayList<>(conditions));
        s.set("actions", new ArrayList<>(actions));
        if (!messages.isEmpty()) {
            s.createSection("messages", messages);
        }
    }

    public Activator copy(String newKey) {
        Activator a = new Activator(newKey);
        a.type = type;
        a.click = click;
        a.slot = slot;
        a.interval = interval;
        a.cooldown = cooldown;
        a.scope = scope;
        a.cooldownMessage = cooldownMessage;
        a.chance = chance;
        a.consumeUsage = consumeUsage;
        a.consumeItem = consumeItem;
        a.cancelEvent = cancelEvent;
        a.entities = entities;
        a.conditions.addAll(conditions);
        a.actions.addAll(actions);
        a.messages.putAll(messages);
        return a;
    }

    public static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
