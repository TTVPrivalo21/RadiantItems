package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Generic editor for a list of text lines (lore, actions, conditions, variables, attributes...). */
public final class ListMenu extends ItemMenu {

    /** Reads/writes the edited list on the live definition. */
    public interface Source {
        List<String> get(CustomItem def);

        void set(CustomItem def, List<String> lines);
    }

    private static final int PAGE = 36;

    private final String title;
    private final Source source;
    private final Function<String, String> validator;
    private final List<String> hint;
    private final Menu back;
    private int page;

    /**
     * @param validator returns an error message for invalid lines, or null when valid
     */
    public ListMenu(RadiantItems plugin, Player viewer, String itemId, String title, Source source,
                    Function<String, String> validator, List<String> hint, Menu back) {
        super(plugin, viewer, itemId);
        this.title = title;
        this.source = source;
        this.validator = validator;
        this.hint = hint;
        this.back = back;
    }

    @Override
    protected String title() {
        return title;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        List<String> lines = source.get(def());
        int pages = Math.max(1, (lines.size() + PAGE - 1) / PAGE);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < PAGE; i++) {
            int index = page * PAGE + i;
            if (index >= lines.size()) {
                break;
            }
            String line = lines.get(index);
            String error = validator == null ? null : validator.apply(line);
            List<String> lore = new ArrayList<>();
            lore.add("<white>" + escape(line));
            if (error != null) {
                lore.add("<red>⚠ " + escape(error));
            }
            lore.add("");
            lore.add("<gray>Clic izq: <white>editar</white>");
            lore.add("<gray>Clic der: <white>borrar</white>");
            lore.add("<gray>Shift+izq: <white>subir</white> · Shift+der: <white>bajar</white>");
            lore.add("<gray>Clic central / Q: <white>insertar debajo</white>");
            button(i, icon(error == null ? Material.PAPER : Material.RED_DYE, "<yellow>#" + (index + 1), lore),
                    e -> lineClick(index, e.getClick()));
        }
        List<String> addLore = new ArrayList<>();
        addLore.add("<gray>Escribe la nueva línea en el chat.");
        if (hint != null && !hint.isEmpty()) {
            addLore.add("");
            hint.forEach(h -> addLore.add("<dark_gray>" + h));
        }
        button(49, icon(Material.EMERALD, "<green>Añadir línea", addLore), e -> ask("Nueva línea:", text -> {
            check(text);
            List<String> list = new ArrayList<>(source.get(def()));
            list.add(text);
            commit(list);
        }));
        button(45, icon(Material.ARROW, "<yellow>Volver"), e -> back.open());
        button(53, icon(Material.LAVA_BUCKET, "<red>Vaciar lista", "<gray>Shift+clic para borrar todas las líneas."), e -> {
            if (e.isShiftClick()) {
                commit(new ArrayList<>());
            }
        });
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
        button(40, icon(Material.BOOK, "<gold>" + lines.size() + " líneas", "<gray>Página " + (page + 1) + "/" + pages), null);
    }

    private void lineClick(int index, ClickType click) {
        List<String> list = new ArrayList<>(source.get(def()));
        if (index >= list.size()) {
            return;
        }
        switch (click) {
            case RIGHT -> {
                list.remove(index);
                commit(list);
            }
            case SHIFT_LEFT -> {
                if (index > 0) {
                    list.add(index - 1, list.remove(index));
                    commit(list);
                }
            }
            case SHIFT_RIGHT -> {
                if (index < list.size() - 1) {
                    list.add(index + 1, list.remove(index));
                    commit(list);
                }
            }
            case MIDDLE, DROP -> ask("Línea a insertar debajo de #" + (index + 1) + ":", text -> {
                check(text);
                List<String> current = new ArrayList<>(source.get(def()));
                current.add(Math.min(index + 1, current.size()), text);
                commit(current);
            });
            default -> {
                viewer.sendMessage(dev.privalo.items.util.Text.parse("<gray>Actual: <white>" + escape(list.get(index))));
                ask("Nuevo texto para la línea #" + (index + 1) + ":", text -> {
                    check(text);
                    List<String> current = new ArrayList<>(source.get(def()));
                    if (index < current.size()) {
                        current.set(index, text);
                        commit(current);
                    }
                });
            }
        }
    }

    private void check(String text) {
        String error = validator == null ? null : validator.apply(text);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    private void commit(List<String> lines) {
        source.set(def(), lines);
        save();
    }
}
