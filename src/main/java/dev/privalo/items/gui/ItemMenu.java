package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import org.bukkit.entity.Player;

import java.io.IOException;

/** Menu bound to one item id; always re-reads the live definition (safe across /ri reload). */
public abstract class ItemMenu extends Menu {

    protected final String itemId;

    protected ItemMenu(RadiantItems plugin, Player viewer, String itemId) {
        super(plugin, viewer);
        this.itemId = itemId;
    }

    protected CustomItem def() {
        return plugin.items().get(itemId);
    }

    @Override
    public void open() {
        if (def() == null) {
            viewer.closeInventory();
            plugin.messages().send(viewer, "unknown-item", "id", itemId);
            return;
        }
        super.open();
    }

    @Override
    public void redraw() {
        if (def() == null) {
            viewer.closeInventory();
            return;
        }
        super.redraw();
    }

    /** Saves items/&lt;id&gt;.yml, refreshes online copies and the pack, then redraws. */
    protected void save() {
        CustomItem def = def();
        if (def == null) {
            return;
        }
        try {
            plugin.items().save(def);
        } catch (IOException ex) {
            plugin.getLogger().severe("No se pudo guardar " + def.id + ": " + ex.getMessage());
            viewer.sendMessage("Error guardando: " + ex.getMessage());
            return;
        }
        plugin.onItemEdited(def);
        redraw();
    }
}
