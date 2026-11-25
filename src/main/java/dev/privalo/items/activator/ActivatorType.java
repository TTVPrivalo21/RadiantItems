package dev.privalo.items.activator;

import org.bukkit.Material;

import java.util.Locale;

/** Every way an item can be triggered. */
public enum ActivatorType {
    RIGHT_CLICK(Material.LIME_DYE, true, false, true, "Clic derecho (sin agacharse)"),
    LEFT_CLICK(Material.RED_DYE, true, false, false, "Clic izquierdo (sin agacharse)"),
    SHIFT_RIGHT_CLICK(Material.LIME_CONCRETE, true, false, true, "Agachado + clic derecho"),
    SHIFT_LEFT_CLICK(Material.RED_CONCRETE, true, false, false, "Agachado + clic izquierdo"),
    HIT_ENTITY(Material.IRON_SWORD, false, false, false, "Golpear a un jugador o mob"),
    KILL_ENTITY(Material.SKELETON_SKULL, false, false, false, "Matar a una entidad"),
    PROJECTILE_HIT(Material.SPECTRAL_ARROW, false, false, false, "Un proyectil del ítem impacta"),
    SHOOT_BOW(Material.BOW, false, false, false, "Disparar con el arco/ballesta"),
    BLOCK_BREAK(Material.IRON_PICKAXE, false, false, false, "Romper un bloque"),
    BLOCK_PLACE(Material.GRASS_BLOCK, false, false, false, "Colocar el ítem como bloque"),
    CONSUME(Material.COOKED_BEEF, false, false, true, "Comer / beber el ítem"),
    DROP_ITEM(Material.HOPPER, false, false, true, "Soltar el ítem (Q)"),
    PICKUP_ITEM(Material.CHEST_MINECART, false, false, false, "Recoger el ítem del suelo"),
    SWAP_HANDS(Material.SHIELD, false, false, true, "Cambiar de mano (F)"),
    EQUIP(Material.LEATHER_CHESTPLATE, false, true, false, "Al equiparlo en la ranura indicada"),
    UNEQUIP(Material.CHAINMAIL_CHESTPLATE, false, true, false, "Al quitarlo de la ranura indicada"),
    HOLD_LOOP(Material.CLOCK, false, true, false, "Periódicamente mientras está en la ranura"),
    SNEAK(Material.LEATHER_BOOTS, false, false, false, "Al agacharse llevándolo en la ranura"),
    JUMP(Material.RABBIT_FOOT, false, false, false, "Al saltar llevándolo en la ranura"),
    DAMAGED(Material.SHIELD, false, false, false, "Al recibir daño de una entidad (target = atacante)"),
    DEATH(Material.WITHER_SKELETON_SKULL, false, false, false, "Al morir llevándolo en la ranura (target = asesino)");

    private final Material icon;
    private final boolean click;
    private final boolean loop;
    private final boolean cancelByDefault;
    private final String description;

    ActivatorType(Material icon, boolean click, boolean loop, boolean cancelByDefault, String description) {
        this.icon = icon;
        this.click = click;
        this.loop = loop;
        this.cancelByDefault = cancelByDefault;
        this.description = description;
    }

    public Material icon() {
        return icon;
    }

    public boolean click() {
        return click;
    }

    public boolean loop() {
        return loop;
    }

    /** Activators that look at hands + armour and honour the "slot" option. */
    public boolean slotted() {
        return loop || this == SNEAK || this == JUMP || this == DAMAGED || this == DEATH;
    }

    public boolean cancelByDefault() {
        return cancelByDefault;
    }

    public String description() {
        return description;
    }

    /** Accepts aliases such as RIGHT, HIT, KILL, SHOOT, EAT, DROP, SWAP, HOLD, LOOP. */
    public static ActivatorType parse(String raw) {
        String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (key) {
            case "RIGHT", "RIGHTCLICK", "CLICK_RIGHT" -> RIGHT_CLICK;
            case "LEFT", "LEFTCLICK", "CLICK_LEFT" -> LEFT_CLICK;
            case "SHIFT_RIGHT", "SNEAK_RIGHT_CLICK" -> SHIFT_RIGHT_CLICK;
            case "SHIFT_LEFT", "SNEAK_LEFT_CLICK" -> SHIFT_LEFT_CLICK;
            case "HIT", "ATTACK", "HIT_PLAYER", "HIT_MOB" -> HIT_ENTITY;
            case "KILL", "KILL_PLAYER", "KILL_MOB" -> KILL_ENTITY;
            case "SHOOT", "BOW_SHOOT" -> SHOOT_BOW;
            case "PROJECTILE", "PROJECTILE_IMPACT" -> PROJECTILE_HIT;
            case "BREAK" -> BLOCK_BREAK;
            case "PLACE" -> BLOCK_PLACE;
            case "EAT", "DRINK" -> CONSUME;
            case "DROP" -> DROP_ITEM;
            case "PICKUP", "PICK_UP", "PICKUP_ITEMS" -> PICKUP_ITEM;
            case "DIE", "ON_DEATH", "PLAYER_DEATH" -> DEATH;
            case "SWAP", "SWAP_HAND", "OFFHAND_SWAP" -> SWAP_HANDS;
            case "HOLD", "LOOP", "PASSIVE", "HELD_LOOP" -> HOLD_LOOP;
            case "CROUCH", "SHIFT", "TOGGLE_SNEAK" -> SNEAK;
            case "HURT", "DAMAGE_TAKEN", "ON_DAMAGE" -> DAMAGED;
            default -> valueOf(key);
        };
    }
}
