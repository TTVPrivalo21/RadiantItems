package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /ri show [carpeta]: browses items/ folder by folder. Sub-folders first, then the items of the folder.
 * Left click gives 1, shift-click gives a stack (needs give permission), right click opens the editor.
 */
public final class BrowserMenu extends Menu {

    private static final int PAGE = 45;

    private final String folder;
    private int page;

    public BrowserMenu(RadiantItems plugin, Player viewer, String folder) {
        super(plugin, viewer);
        this.folder = folder == null ? "" : folder;
    }

    @Override
    protected String title() {
        return "<dark_gray>Ítems · <#F97316>" + (folder.isEmpty() ? "todas las carpetas" : folder);
    }

    @Override
    protected int rows() {
        return 6;
    }

    /** Direct sub-folders of {@code parent} ("" = top level). */
    private List<String> childFolders() {
        List<String> out = new ArrayList<>();
        for (String category : plugin.items().categories()) {
            String parent = category.contains("/") ? category.substring(0, category.lastIndexOf('/')) : "";
            if (parent.equals(folder)) {
                out.add(category);
            }
        }
        return out;
    }

    private List<CustomItem> itemsHere() {
        List<CustomItem> out = new ArrayList<>();
        for (CustomItem def : plugin.items().all()) {
            if (def.category.equals(folder)) {
                out.add(def);
            }
        }
        return out;
    }

    @Override
    protected void draw() {
        List<String> folders = childFolders();
        List<CustomItem> items = itemsHere();
        int total = folders.size() + items.size();
        int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        page = Math.min(page, pages - 1);
        boolean canGive = viewer.hasPermission("radiantitems.command.give") || viewer.hasPermission("radiantitems.admin");
        boolean canEdit = viewer.hasPermission("radiantitems.command.edit") || viewer.hasPermission("radiantitems.admin");
        for (int i = 0; i < PAGE; i++) {
            int index = page * PAGE + i;
            if (index >= total) {
                break;
            }
            if (index < folders.size()) {
                String child = folders.get(index);
                int count = plugin.items().inCategory(child).size();
                String name = child.substring(child.lastIndexOf('/') + 1);
                button(i, icon(Material.CHEST, "<#FB923C>📁 " + name, "<white>" + count + " ítems", "", "<gray>Clic para abrir"),
                        e -> new BrowserMenu(plugin, viewer, child).open());
                continue;
            }
            CustomItem def = items.get(index - folders.size());
            ItemStack preview = plugin.factory().create(def, 1);
            ItemMeta meta = preview.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.item(""));
            lore.add(Text.item("<dark_gray>id: " + def.id + " · " + def.activators.size() + " activadores"));
            if (def.permission != null) {
                lore.add(Text.item("<dark_gray>permiso: " + def.permission));
            }
            if (canGive) {
                lore.add(Text.item("<gray>Clic: <white>obtener 1</white> · Shift+clic: <white>pila</white>"));
            }
            if (canEdit) {
                lore.add(Text.item("<gray>Clic derecho: <white>editar</white>"));
            }
            meta.lore(lore);
            preview.setItemMeta(meta);
            String id = def.id;
            button(i, preview, e -> {
                CustomItem current = plugin.items().get(id);
                if (current == null) {
                    redraw();
                    return;
                }
                if (e.getClick() == ClickType.RIGHT && canEdit) {
                    new EditorMenu(plugin, viewer, id).open();
                } else if (canGive && (e.getClick() == ClickType.LEFT || e.isShiftClick())) {
                    int amount = e.isShiftClick() ? 64 : 1;
                    plugin.giveStacks(current, amount).forEach(s -> viewer.getInventory().addItem(s)
                            .values().forEach(o -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), o)));
                    plugin.messages().send(viewer, "received", "amount", String.valueOf(amount), "item", Text.plain(current.displayName()));
                }
            });
        }
        if (!folder.isEmpty()) {
            String parent = folder.contains("/") ? folder.substring(0, folder.lastIndexOf('/')) : "";
            button(45, icon(Material.ARROW, "<yellow>Carpeta superior", "<gray>items/" + parent), e -> new BrowserMenu(plugin, viewer, parent).open());
        }
        if (page > 0) {
            button(48, icon(Material.SPECTRAL_ARROW, "<yellow>Página anterior"), e -> {
                page--;
                redraw();
            });
        }
        button(49, icon(Material.BOOK, "<#F97316>items/" + folder, "<white>" + folders.size() + " carpetas · " + items.size() + " ítems",
                "<gray>Página " + (page + 1) + "/" + pages), null);
        if (page < pages - 1) {
            button(50, icon(Material.SPECTRAL_ARROW, "<yellow>Página siguiente"), e -> {
                page++;
                redraw();
            });
        }
        button(53, icon(Material.BARRIER, "<red>Cerrar"), e -> viewer.closeInventory());
    }
}
