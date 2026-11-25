package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Base inventory menu: slot → button handler, redraw on demand. */
public abstract class Menu implements InventoryHolder {

    protected final RadiantItems plugin;
    protected final Player viewer;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();
    private Inventory inventory;

    protected Menu(RadiantItems plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract String title();

    protected abstract int rows();

    protected abstract void draw();

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, rows() * 9, Text.parse(title()));
        redraw();
        viewer.openInventory(inventory);
    }

    public void redraw() {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        handlers.clear();
        draw();
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    protected void button(int slot, ItemStack icon, Consumer<InventoryClickEvent> handler) {
        inventory.setItem(slot, icon);
        if (handler != null) {
            handlers.put(slot, handler);
        }
    }

    void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    /** Closes the menu, asks for chat input, then reopens this menu. */
    protected void ask(String prompt, Consumer<String> onInput) {
        viewer.closeInventory();
        plugin.chat().request(viewer, prompt, text -> {
            onInput.accept(text);
            open();
        }, this::open);
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        List<String> lines = new ArrayList<>(List.of(lore));
        return icon(material, name, lines);
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material == null || material.isAir() || !material.isItem() ? Material.BARRIER : material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Text.item(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            for (String part : line.split("\n")) {
                lines.add(Text.item(part));
            }
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack glow(ItemStack stack, boolean on) {
        ItemMeta meta = stack.getItemMeta();
        meta.setEnchantmentGlintOverride(on);
        stack.setItemMeta(meta);
        return stack;
    }

    protected static String onOff(boolean value) {
        return value ? "<green>Sí</green>" : "<red>No</red>";
    }

    /** Escapes MiniMessage tags so raw YAML text can be shown inside lore. */
    protected static String escape(String raw) {
        return raw == null ? "" : raw.replace("<", "\\<");
    }
}
