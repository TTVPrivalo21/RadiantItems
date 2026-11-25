package dev.privalo.items.command;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.activator.Activator;
import dev.privalo.items.gui.EditorMenu;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.item.ItemLoader;
import dev.privalo.items.pack.BuiltPack;
import dev.privalo.items.util.Expr;
import dev.privalo.items.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** /radiantitems (/ri): give, create, edit, list, info, setuses, pack, reload, delete. */
public final class ItemsCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("give", "givefolder", "drop", "show", "browser", "create", "edit", "list", "info", "setuses", "pack", "reload", "delete", "help");

    private final RadiantItems plugin;

    public ItemsCommand(RadiantItems plugin) {
        this.plugin = plugin;
    }

    private boolean allowed(CommandSender sender, String sub) {
        if (sender.hasPermission("radiantitems.command." + sub) || sender.hasPermission("radiantitems.admin")) {
            return true;
        }
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, label, args);
            case "givefolder" -> giveFolder(sender, label, args);
            case "drop" -> drop(sender, label, args);
            case "show", "browser" -> show(sender, args);
            case "create" -> create(sender, label, args);
            case "edit" -> edit(sender, label, args);
            case "list" -> list(sender, args);
            case "info" -> info(sender);
            case "setuses" -> setUses(sender, label, args);
            case "pack" -> pack(sender, args);
            case "reload" -> {
                if (allowed(sender, "reload")) {
                    int count = plugin.reload();
                    plugin.messages().send(sender, "reloaded", "count", String.valueOf(count));
                }
            }
            case "delete" -> delete(sender, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        String l = "/" + label;
        String[] lines = {
                "<gradient:#ffd36e:#ff8a3d><b>RadiantItems</b></gradient> <gray>v" + plugin.getPluginMeta().getVersion(),
                "<yellow>" + l + " give <jugador> <item> [cantidad]</yellow> <gray>dar un ítem",
                "<yellow>" + l + " givefolder <jugador> <carpeta> [cantidad]</yellow> <gray>dar todos los ítems de una carpeta",
                "<yellow>" + l + " drop <id> <mundo> <x> <y> <z> [cantidad]</yellow> <gray>soltar el ítem en el suelo",
                "<yellow>" + l + " show [carpeta]</yellow> <gray>navegador visual de ítems (alias browser)",
                "<yellow>" + l + " create <id> [carpeta]</yellow> <gray>crear (en items/<carpeta>/) y abrir el editor",
                "<yellow>" + l + " edit <id></yellow> <gray>abrir el editor visual",
                "<yellow>" + l + " list [carpeta]</yellow> <gray>ítems cargados, agrupados por carpeta",
                "<yellow>" + l + " info</yellow> <gray>datos del ítem en tu mano",
                "<yellow>" + l + " setuses <cantidad></yellow> <gray>usos del ítem en tu mano",
                "<yellow>" + l + " pack [rebuild|status]</yellow> <gray>resource pack",
                "<yellow>" + l + " reload</yellow> <gray>recargar config, ítems y pack",
                "<yellow>" + l + " delete <id></yellow> <gray>mover el ítem a items/.trash/"};
        for (String line : lines) {
            sender.sendMessage(Text.parse(line));
        }
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, "give")) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.parse("<red>Uso: /" + label + " give <jugador> <item> [cantidad]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return;
        }
        CustomItem def = plugin.items().get(args[2]);
        if (def == null) {
            plugin.messages().send(sender, "unknown-item", "id", args[2]);
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            if (!Expr.isNumeric(args[3])) {
                plugin.messages().send(sender, "invalid-number", "value", args[3]);
                return;
            }
            amount = (int) Math.max(1, Math.min(2304, Expr.eval(args[3])));
        }
        List<ItemStack> stacks = plugin.giveStacks(def, amount);
        target.getInventory().addItem(stacks.toArray(new ItemStack[0]))
                .values().forEach(s -> target.getWorld().dropItemNaturally(target.getLocation(), s));
        String name = Text.plain(Text.parse(def.displayName()));
        plugin.messages().send(sender, "given", "amount", String.valueOf(amount), "item", name, "player", target.getName());
        if (target != sender) {
            plugin.messages().send(target, "received", "amount", String.valueOf(amount), "item", name);
        }
    }

    private void create(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, "create")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Uso: /" + label + " create <id>"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!ItemLoader.ID.matcher(id).matches()) {
            plugin.messages().send(sender, "invalid-id");
            return;
        }
        if (plugin.items().exists(id)) {
            plugin.messages().send(sender, "already-exists", "id", id);
            return;
        }
        CustomItem def = ItemLoader.blank(id);
        if (args.length > 2) {
            def.category = dev.privalo.items.item.ItemRegistry.cleanCategory(args[2]);
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.getType().isAir() && ItemData.id(hand) == null) {
            def.material = hand.getType().name();
            def.revision = ItemLoader.revision(def);
        }
        try {
            plugin.items().add(def);
        } catch (IOException ex) {
            sender.sendMessage(Text.parse("<red>No se pudo guardar: " + ex.getMessage()));
            return;
        }
        plugin.messages().send(sender, "created", "id", id);
        new EditorMenu(plugin, player, id).open();
    }

    private void edit(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, "edit")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        String id = args.length >= 2 ? args[1] : ItemData.id(player.getInventory().getItemInMainHand());
        if (id == null) {
            sender.sendMessage(Text.parse("<red>Uso: /" + label + " edit <id> (o sostén el ítem)"));
            return;
        }
        if (plugin.items().get(id) == null) {
            plugin.messages().send(sender, "unknown-item", "id", id);
            return;
        }
        new EditorMenu(plugin, player, id.toLowerCase(Locale.ROOT)).open();
    }

    private void list(CommandSender sender, String[] args) {
        if (!allowed(sender, "list")) {
            return;
        }
        String filter = args.length > 1 ? dev.privalo.items.item.ItemRegistry.cleanCategory(args[1]) : "";
        List<CustomItem> shown = plugin.items().inCategory(filter);
        plugin.messages().send(sender, "list-header", "count", String.valueOf(shown.size()), "filter", filter.isEmpty() ? "" : " · " + filter);
        Map<String, List<CustomItem>> byCategory = new java.util.TreeMap<>();
        for (CustomItem def : shown) {
            byCategory.computeIfAbsent(def.category.isEmpty() ? "(raíz)" : def.category, k -> new ArrayList<>()).add(def);
        }
        byCategory.forEach((category, defs) -> {
            plugin.messages().send(sender, "list-category", "category", category, "count", String.valueOf(defs.size()));
            for (CustomItem def : defs) {
                plugin.messages().send(sender, "list-entry", "id", def.id, "name", def.displayName(), "material", def.material,
                        "activators", String.valueOf(def.activators.size()));
            }
        });
        if (filter.isEmpty()) {
            plugin.messages().send(sender, "list-categories", "categories", String.join(", ", plugin.items().categories()));
        }
    }

    private void info(CommandSender sender) {
        if (!allowed(sender, "info")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        CustomItem def = plugin.items().get(ItemData.id(hand));
        if (def == null) {
            plugin.messages().send(sender, ItemData.forged(hand) ? "forged-item" : "not-custom");
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        UUID uuid = ItemData.uuid(pdc);
        String owner = ItemData.ownerName(pdc);
        Map<String, String> vars = ItemData.effectiveVars(def, meta);
        String activators = def.activators.stream().map(ItemsCommand::describe).collect(Collectors.joining(", "));
        plugin.messages().send(sender, "info", "id", def.id, "name", def.displayName(), "material", def.material,
                "uses", plugin.factory().usesText(def, meta),
                "max_uses", def.maxUses <= 0 ? plugin.getConfig().getString("format.unlimited-uses", "∞") : String.valueOf(def.maxUses),
                "uuid", uuid == null ? "-" : uuid.toString().substring(0, 8),
                "owner", owner == null ? "-" : owner,
                "vars", vars.isEmpty() ? "-" : vars.toString(),
                "activators", activators.isEmpty() ? "-" : activators);
    }

    private void setUses(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, "setuses")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Uso: /" + label + " setuses <cantidad>"));
            return;
        }
        if (!Expr.isNumeric(args[1])) {
            plugin.messages().send(sender, "invalid-number", "value", args[1]);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        CustomItem def = plugin.items().get(ItemData.id(hand));
        if (def == null) {
            plugin.messages().send(sender, "not-custom");
            return;
        }
        if (def.maxUses <= 0) {
            plugin.messages().send(sender, "setuses-unlimited");
            return;
        }
        int value = (int) Math.max(0, Math.min(def.maxUses, Expr.eval(args[1])));
        ItemMeta meta = hand.getItemMeta();
        ItemData.setUses(meta.getPersistentDataContainer(), value);
        plugin.factory().render(def, meta, player);
        hand.setItemMeta(meta);
        player.getInventory().setItemInMainHand(hand);
        plugin.messages().send(sender, "setuses-done", "uses", String.valueOf(value), "max_uses", String.valueOf(def.maxUses));
    }

    private void pack(CommandSender sender, String[] args) {
        if (!allowed(sender, "pack")) {
            return;
        }
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "send";
        if ((mode.equals("rebuild") || mode.equals("status")) && !sender.hasPermission("radiantitems.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        switch (mode) {
            case "rebuild" -> plugin.pack().rebuild().thenAccept(changed -> {
                BuiltPack pack = plugin.pack().current();
                if (pack == null) {
                    plugin.messages().send(sender, "pack-unavailable");
                    return;
                }
                plugin.messages().send(sender, "pack-rebuilt", "sha1", pack.sha1Hex().substring(0, 12));
                if (changed) {
                    plugin.pack().sendAll();
                }
            });
            case "status" -> {
                BuiltPack pack = plugin.pack().current();
                if (pack == null) {
                    plugin.messages().send(sender, "pack-unavailable");
                    return;
                }
                sender.sendMessage(Text.parse("<gold>Pack: <white>" + pack.entries() + " archivos, " + pack.zip().length / 1024 + " KB"));
                sender.sendMessage(Text.parse("<gray>SHA-1: <white>" + pack.sha1Hex()));
                sender.sendMessage(Text.parse("<gray>URL: <white>" + plugin.pack().url(pack)));
                pack.textures().forEach((id, src) -> sender.sendMessage(Text.parse("<dark_gray>• <yellow>" + id + "</yellow> <gray>← " + src)));
            }
            default -> {
                if (!(sender instanceof Player player)) {
                    plugin.messages().send(sender, "player-only");
                    return;
                }
                if (plugin.pack().send(player, true)) {
                    plugin.messages().send(sender, "pack-sent");
                } else {
                    plugin.messages().send(sender, "pack-unavailable");
                }
            }
        }
    }

    /** "browser" shares the permission of "show". */
    private static String permissionOf(String sub) {
        return sub.equals("browser") ? "show" : sub;
    }

    private void giveFolder(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, "givefolder")) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.parse("<red>Uso: /" + label + " givefolder <jugador> <carpeta> [cantidad]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[1]);
            return;
        }
        String folder = dev.privalo.items.item.ItemRegistry.cleanCategory(args[2]);
        List<CustomItem> defs = folder.isEmpty() ? List.of() : plugin.items().inCategory(folder);
        if (defs.isEmpty()) {
            plugin.messages().send(sender, "unknown-folder", "folder", args[2], "folders", String.join(", ", plugin.items().categories()));
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            if (!Expr.isNumeric(args[3])) {
                plugin.messages().send(sender, "invalid-number", "value", args[3]);
                return;
            }
            amount = (int) Math.max(1, Math.min(64, Expr.eval(args[3])));
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (CustomItem def : defs) {
            stacks.addAll(plugin.giveStacks(def, amount));
        }
        int dropped = 0;
        for (ItemStack overflow : target.getInventory().addItem(stacks.toArray(new ItemStack[0])).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), overflow);
            dropped++;
        }
        plugin.messages().send(sender, "given-folder", "count", String.valueOf(defs.size()), "amount", String.valueOf(amount),
                "folder", folder, "player", target.getName(), "dropped", String.valueOf(dropped));
        if (target != sender) {
            plugin.messages().send(target, "received-folder", "count", String.valueOf(defs.size()), "folder", folder);
        }
    }

    private void drop(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, "drop")) {
            return;
        }
        if (args.length < 6) {
            sender.sendMessage(Text.parse("<red>Uso: /" + label + " drop <id> <mundo> <x> <y> <z> [cantidad]"));
            return;
        }
        CustomItem def = plugin.items().get(args[1]);
        if (def == null) {
            plugin.messages().send(sender, "unknown-item", "id", args[1]);
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            plugin.messages().send(sender, "unknown-world", "world", args[2]);
            return;
        }
        org.bukkit.Location base = sender instanceof Player p && p.getWorld().equals(world) ? p.getLocation() : world.getSpawnLocation();
        double[] xyz = new double[3];
        for (int i = 0; i < 3; i++) {
            String raw = args[3 + i];
            double origin = i == 0 ? base.getX() : i == 1 ? base.getY() : base.getZ();
            boolean relative = raw.startsWith("~");
            String expr = relative ? (raw.length() > 1 ? raw.substring(1) : "0") : raw;
            if (!Expr.isNumeric(expr)) {
                plugin.messages().send(sender, "invalid-number", "value", raw);
                return;
            }
            xyz[i] = (relative ? origin : 0) + Expr.eval(expr);
        }
        int amount = 1;
        if (args.length > 6) {
            if (!Expr.isNumeric(args[6])) {
                plugin.messages().send(sender, "invalid-number", "value", args[6]);
                return;
            }
            amount = (int) Math.max(1, Math.min(2304, Expr.eval(args[6])));
        }
        org.bukkit.Location at = new org.bukkit.Location(world, xyz[0], xyz[1], xyz[2]);
        world.getChunkAt(at).load(true);
        for (ItemStack stack : plugin.giveStacks(def, amount)) {
            world.dropItem(at, stack);
        }
        plugin.messages().send(sender, "dropped", "amount", String.valueOf(amount), "item", Text.plain(def.displayName()),
                "world", world.getName(), "x", Expr.format(xyz[0]), "y", Expr.format(xyz[1]), "z", Expr.format(xyz[2]));
    }

    private void show(CommandSender sender, String[] args) {
        if (!allowed(sender, "show")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        String folder = args.length > 1 ? dev.privalo.items.item.ItemRegistry.cleanCategory(args[1]) : "";
        if (!folder.isEmpty() && plugin.items().inCategory(folder).isEmpty()) {
            plugin.messages().send(sender, "unknown-folder", "folder", args[1], "folders", String.join(", ", plugin.items().categories()));
            return;
        }
        new dev.privalo.items.gui.BrowserMenu(plugin, player, folder).open();
    }

    private void delete(CommandSender sender, String label, String[] args) {
        if (!allowed(sender, "delete")) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.parse("<red>Uso: /" + label + " delete <id>"));
            return;
        }
        try {
            if (plugin.items().delete(args[1])) {
                plugin.messages().send(sender, "deleted", "id", args[1].toLowerCase(Locale.ROOT));
            } else {
                plugin.messages().send(sender, "unknown-item", "id", args[1]);
            }
        } catch (IOException ex) {
            sender.sendMessage(Text.parse("<red>Error: " + ex.getMessage()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBS) {
                if (sub.equals("help") || sender.hasPermission("radiantitems.command." + permissionOf(sub)) || sender.hasPermission("radiantitems.admin")) {
                    options.add(sub);
                }
            }
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "give" -> {
                    if (args.length == 2) {
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    } else if (args.length == 3) {
                        options.addAll(plugin.items().ids());
                    } else if (args.length == 4) {
                        options.addAll(List.of("1", "16", "64"));
                    }
                }
                case "givefolder" -> {
                    if (args.length == 2) {
                        Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    } else if (args.length == 3) {
                        options.addAll(plugin.items().categories());
                    } else if (args.length == 4) {
                        options.addAll(List.of("1", "16", "64"));
                    }
                }
                case "drop" -> {
                    if (args.length == 2) {
                        options.addAll(plugin.items().ids());
                    } else if (args.length == 3) {
                        Bukkit.getWorlds().forEach(w -> options.add(w.getName()));
                    } else if (args.length >= 4 && args.length <= 6) {
                        options.add("~");
                        if (sender instanceof Player p) {
                            org.bukkit.Location l = p.getLocation();
                            options.add(String.valueOf(args.length == 4 ? l.getBlockX() : args.length == 5 ? l.getBlockY() : l.getBlockZ()));
                        }
                    } else if (args.length == 7) {
                        options.addAll(List.of("1", "16", "64"));
                    }
                }
                case "show", "browser" -> {
                    if (args.length == 2) {
                        options.addAll(plugin.items().categories());
                    }
                }
                case "list" -> {
                    if (args.length == 2) {
                        options.addAll(plugin.items().categories());
                    }
                }
                case "edit", "delete" -> {
                    if (args.length == 2) {
                        options.addAll(plugin.items().ids());
                    }
                }
                case "create" -> {
                    if (args.length == 2) {
                        options.add("<id>");
                    } else if (args.length == 3) {
                        options.addAll(plugin.items().categories());
                    }
                }
                case "setuses" -> {
                    if (args.length == 2 && sender instanceof Player player) {
                        CustomItem def = plugin.items().get(ItemData.id(player.getInventory().getItemInMainHand()));
                        if (def != null && def.maxUses > 0) {
                            options.addAll(List.of("0", String.valueOf(def.maxUses / 2), String.valueOf(def.maxUses)));
                        }
                    }
                }
                case "pack" -> {
                    if (args.length == 2) {
                        options.add("send");
                        if (sender.hasPermission("radiantitems.admin")) {
                            options.addAll(List.of("rebuild", "status"));
                        }
                    }
                }
                default -> {
                    // no further arguments
                }
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).sorted().collect(Collectors.toList());
    }

    /** "key(TYPE)" used by /ri info. */
    static String describe(Activator a) {
        return a.key + "(" + a.type.name() + ")";
    }
}
