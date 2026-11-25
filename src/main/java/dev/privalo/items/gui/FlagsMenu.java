package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

/** Toggles every {@link ItemFlag} plus the unbreakable tag. */
public final class FlagsMenu extends ItemMenu {

    private final Menu back;

    public FlagsMenu(RadiantItems plugin, Player viewer, String itemId, Menu back) {
        super(plugin, viewer, itemId);
        this.back = back;
    }

    @Override
    protected String title() {
        return "<dark_gray>Flags · <gold>" + itemId;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void draw() {
        CustomItem d = def();
        int slot = 0;
        for (ItemFlag flag : ItemFlag.values()) {
            if (slot >= 17) {
                break;
            }
            boolean on = d.flags.contains(flag.name());
            button(slot++, glow(icon(on ? Material.LIME_DYE : Material.GRAY_DYE, (on ? "<green>" : "<gray>") + Text.pretty(flag.name()),
                    "<gray>" + flag.name(), "<gray>Activo: " + onOff(on), "<gray>Clic para alternar"), on), e -> {
                CustomItem def = def();
                if (!def.flags.remove(flag.name())) {
                    def.flags.add(flag.name());
                }
                save();
            });
        }
        button(17, glow(icon(Material.ANVIL, "<gold>Irrompible", "<gray>Activo: " + onOff(d.unbreakable)), d.unbreakable), e -> {
            def().unbreakable = !def().unbreakable;
            save();
        });
        button(18, icon(Material.ARROW, "<yellow>Volver"), e -> back.open());
        button(22, icon(Material.SPYGLASS, "<gold>Ocultar todo", "<gray>Activa todos los HIDE_*"), e -> {
            CustomItem def = def();
            for (ItemFlag flag : ItemFlag.values()) {
                if (flag.name().startsWith("HIDE_") && !def.flags.contains(flag.name())) {
                    def.flags.add(flag.name());
                }
            }
            save();
        });
        button(26, icon(Material.GRINDSTONE, "<red>Quitar todos", "<gray>Shift+clic"), e -> {
            if (e.isShiftClick()) {
                def().flags.clear();
                save();
            }
        });
    }
}
