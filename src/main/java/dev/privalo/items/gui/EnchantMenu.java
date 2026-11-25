package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.util.Expr;
import dev.privalo.items.util.Text;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Every registered enchantment: left +1, right -1, shift-left exact level. */
public final class EnchantMenu extends ItemMenu {

    private static final int PAGE = 45;
    private final Menu back;
    private int page;

    public EnchantMenu(RadiantItems plugin, Player viewer, String itemId, Menu back) {
        super(plugin, viewer, itemId);
        this.back = back;
    }

    @Override
    protected String title() {
        return "<dark_gray>Encantamientos · <gold>" + itemId;
    }

    @Override
    protected int rows() {
        return 6;
    }

    private static List<Enchantment> all() {
        List<Enchantment> list = new ArrayList<>();
        Registry.ENCHANTMENT.forEach(list::add);
        list.sort(Comparator.comparing(e -> e.getKey().toString()));
        return list;
    }

    @Override
    protected void draw() {
        CustomItem d = def();
        List<Enchantment> list = all();
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < PAGE; i++) {
            int index = page * PAGE + i;
            if (index >= list.size()) {
                break;
            }
            Enchantment enchantment = list.get(index);
            String key = enchantment.getKey().getKey();
            int level = d.enchantments.getOrDefault(key, 0);
            button(i, glow(icon(level > 0 ? Material.ENCHANTED_BOOK : Material.BOOK,
                    (level > 0 ? "<aqua>" : "<gray>") + Text.pretty(key) + (level > 0 ? " " + level : ""),
                    "<gray>Nivel: <white>" + level + "</white> <dark_gray>(máx. vanilla " + enchantment.getMaxLevel() + ")", "",
                    "<gray>Izq: <white>+1</white> · Der: <white>-1</white>", "<gray>Shift+izq: <white>escribir nivel</white>"), level > 0), e -> {
                switch (e.getClick()) {
                    case LEFT -> setLevel(key, level + 1);
                    case RIGHT -> setLevel(key, level - 1);
                    case SHIFT_LEFT -> ask("Nivel de " + Text.pretty(key) + " (0 = quitar, máx. 255):", text -> {
                        if (!Expr.isNumeric(text)) {
                            throw new IllegalArgumentException("'" + text + "' no es un número");
                        }
                        setLevel(key, (int) Expr.eval(text));
                    });
                    default -> {
                        // other clicks ignored
                    }
                }
            });
        }
        button(45, icon(Material.ARROW, "<yellow>Volver"), e -> back.open());
        if (page > 0) {
            button(48, icon(Material.SPECTRAL_ARROW, "<yellow>Página anterior"), e -> {
                page--;
                redraw();
            });
        }
        if (page < pages - 1) {
            button(50, icon(Material.SPECTRAL_ARROW, "<yellow>Página siguiente"), e -> {
                page++;
                redraw();
            });
        }
        button(53, icon(Material.GRINDSTONE, "<red>Quitar todos", "<gray>Shift+clic"), e -> {
            if (e.isShiftClick()) {
                def().enchantments.clear();
                save();
            }
        });
    }

    private void setLevel(String key, int level) {
        CustomItem d = def();
        if (level <= 0) {
            d.enchantments.remove(key);
        } else {
            d.enchantments.put(key, Math.min(255, level));
        }
        save();
    }
}
