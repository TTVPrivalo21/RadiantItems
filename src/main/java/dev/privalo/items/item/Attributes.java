package dev.privalo.items.item;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.List;
import java.util.Locale;

/**
 * Registry-based attribute lookup: works with 1.21.x ("generic.attack_damage" / "attack_damage")
 * and newer versions without touching renamed enum constants.
 */
public final class Attributes {

    /** Shown in the editor. */
    public static final List<String> COMMON = List.of(
            "attack_damage", "attack_speed", "movement_speed", "max_health", "armor", "armor_toughness",
            "knockback_resistance", "attack_knockback", "luck", "scale", "step_height", "jump_strength",
            "block_interaction_range", "entity_interaction_range", "gravity", "safe_fall_distance");

    private Attributes() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    public static Attribute resolve(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (key.startsWith("generic_")) {
            key = key.substring("generic_".length());
        } else if (key.startsWith("player_")) {
            key = key.substring("player_".length());
        }
        if (key.startsWith("generic.") || key.startsWith("player.")) {
            key = key.substring(key.indexOf('.') + 1);
        }
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        if (attribute == null) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + key));
        }
        if (attribute == null) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("player." + key));
        }
        return attribute;
    }

    /** Parsed "ATTACK_DAMAGE: 8 [slot] [operation]". */
    public record Spec(String attribute, double amount, String slot, AttributeModifier.Operation operation) {
    }

    public static Spec parse(String line) {
        String clean = line.trim();
        int colon = clean.indexOf(':');
        String name;
        String rest;
        if (colon >= 0) {
            name = clean.substring(0, colon).trim();
            rest = clean.substring(colon + 1).trim();
        } else {
            String[] p = clean.split("\\s+", 2);
            name = p[0];
            rest = p.length > 1 ? p[1] : "";
        }
        String[] parts = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Atributo sin cantidad: " + line);
        }
        double amount;
        try {
            amount = Double.parseDouble(parts[0]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Cantidad inválida en atributo: " + line);
        }
        String slot = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "any";
        AttributeModifier.Operation op = AttributeModifier.Operation.ADD_NUMBER;
        if (parts.length > 2) {
            String o = parts[2].toUpperCase(Locale.ROOT);
            op = switch (o) {
                case "ADD", "ADD_NUMBER", "ADD_VALUE" -> AttributeModifier.Operation.ADD_NUMBER;
                case "MULTIPLY", "ADD_SCALAR", "ADD_MULTIPLIED_BASE" -> AttributeModifier.Operation.ADD_SCALAR;
                case "MULTIPLY_SCALAR_1", "ADD_MULTIPLIED_TOTAL" -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                default -> throw new IllegalArgumentException("Operación desconocida '" + parts[2] + "' (ADD, MULTIPLY, MULTIPLY_SCALAR_1)");
            };
        }
        return new Spec(name, amount, slot, op);
    }

    public static EquipmentSlotGroup slot(String raw) {
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(raw.toLowerCase(Locale.ROOT).replace("_", ""));
        if (group == null) {
            group = EquipmentSlotGroup.getByName(raw.toLowerCase(Locale.ROOT));
        }
        return group == null ? EquipmentSlotGroup.ANY : group;
    }
}
