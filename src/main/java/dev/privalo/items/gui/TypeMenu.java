package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.activator.Activator;
import dev.privalo.items.activator.ActivatorType;
import org.bukkit.entity.Player;

/** Chooses an {@link ActivatorType}: creates a new activator or changes an existing one. */
public final class TypeMenu extends ItemMenu {

    private final String activatorKey;
    private final Menu back;

    public TypeMenu(RadiantItems plugin, Player viewer, String itemId, String activatorKey, Menu back) {
        super(plugin, viewer, itemId);
        this.activatorKey = activatorKey;
        this.back = back;
    }

    @Override
    protected String title() {
        return "<dark_gray>Tipo de activador";
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void draw() {
        Activator current = activatorKey == null ? null : def().activator(activatorKey);
        int slot = 0;
        for (ActivatorType type : ActivatorType.values()) {
            boolean selected = current != null && current.type == type;
            button(slot++, glow(icon(type.icon(), (selected ? "<green>" : "<yellow>") + type.name(), "<gray>" + type.description(),
                    type.slotted() ? "<dark_gray>Usa la opción de ranura (slot)." : "",
                    type.click() ? "<dark_gray>Usa la opción aire/bloque." : ""), selected), e -> choose(type));
        }
        button(26, icon(org.bukkit.Material.ARROW, "<yellow>Volver"), e -> back.open());
    }

    private void choose(ActivatorType type) {
        if (activatorKey == null) {
            if (back instanceof ActivatorListMenu list) {
                list.create(type);
            }
            return;
        }
        Activator a = def().activator(activatorKey);
        if (a != null) {
            a.type = type;
            save();
        }
        back.open();
    }
}
