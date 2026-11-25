package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.activator.Activator;
import dev.privalo.items.activator.ActivatorType;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.util.Expr;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** Lists the item's activators: open, duplicate, delete, add new. */
public final class ActivatorListMenu extends ItemMenu {

    private final Menu back;

    public ActivatorListMenu(RadiantItems plugin, Player viewer, String itemId, Menu back) {
        super(plugin, viewer, itemId);
        this.back = back;
    }

    @Override
    protected String title() {
        return "<dark_gray>Activadores · <gold>" + itemId;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        CustomItem d = def();
        for (int i = 0; i < Math.min(45, d.activators.size()); i++) {
            Activator a = d.activators.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Tipo: <white>" + a.type.name() + " <dark_gray>(" + a.type.description() + ")");
            if (a.cooldown > 0) {
                lore.add("<gray>Cooldown: <white>" + Expr.format(a.cooldown) + "s <dark_gray>(" + a.scope + ")");
            }
            if (a.chance < 100) {
                lore.add("<gray>Probabilidad: <white>" + Expr.format(a.chance) + "%");
            }
            if (a.consumeUsage > 0) {
                lore.add("<gray>Consume usos: <white>" + a.consumeUsage);
            }
            if (a.consumeItem) {
                lore.add("<gray>Consume el ítem");
            }
            lore.add("<gray>Condiciones: <white>" + a.conditions.size() + " <gray>· Acciones: <white>" + a.actions.size());
            for (int j = 0; j < Math.min(4, a.actions.size()); j++) {
                lore.add("<dark_gray>  " + escape(a.actions.get(j)));
            }
            lore.add("");
            lore.add("<gray>Clic: <white>editar</white> · Shift+izq: <white>duplicar</white>");
            lore.add("<gray>Shift+der: <red>eliminar</red>");
            String key = a.key;
            button(i, icon(a.type.icon(), "<yellow>" + a.key, lore), e -> {
                if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    def().activators.removeIf(x -> x.key.equals(key));
                    save();
                } else if (e.getClick() == ClickType.SHIFT_LEFT) {
                    Activator source = def().activator(key);
                    if (source != null) {
                        def().activators.add(source.copy(def().nextActivatorKey(key)));
                        save();
                    }
                } else {
                    new ActivatorMenu(plugin, viewer, itemId, key, this).open();
                }
            });
        }
        button(45, icon(Material.ARROW, "<yellow>Volver"), e -> back.open());
        button(49, icon(Material.EMERALD, "<green>Nuevo activador", "<gray>Elige el tipo de disparador."),
                e -> new TypeMenu(plugin, viewer, itemId, null, this).open());
    }

    /** Called by {@link TypeMenu} when creating. */
    void create(ActivatorType type) {
        CustomItem d = def();
        Activator a = new Activator(d.nextActivatorKey(type.name()));
        a.type = type;
        if (type.loop()) {
            a.slot = Activator.SlotMode.HAND;
        }
        d.activators.add(a);
        save();
        new ActivatorMenu(plugin, viewer, itemId, a.key, this).open();
    }
}
