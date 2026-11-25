package dev.privalo.items.gui;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.item.Attributes;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.util.Expr;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Main editor of an item: every property, restrictions and the entry to the activators. */
public final class EditorMenu extends ItemMenu {

    public EditorMenu(RadiantItems plugin, Player viewer, String itemId) {
        super(plugin, viewer, itemId);
    }

    @Override
    protected String title() {
        return "<dark_gray>Editor · <gold>" + itemId;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        CustomItem d = def();
        ItemStack preview = plugin.factory().create(d, 1);
        button(4, preview, e -> {
            viewer.getInventory().addItem(plugin.factory().create(def(), 1));
            viewer.sendMessage(dev.privalo.items.util.Text.parse("<green>Copia de vista previa añadida a tu inventario."));
        });

        // ── row 1: appearance ──
        button(10, icon(iconMaterial(d), "<gold>Material", "<gray>Actual: <white>" + d.material, "",
                "<gray>Clic con un ítem en el cursor: <white>usar ese material</white>",
                "<gray>Clic sin cursor: <white>escribirlo en el chat</white>"), this::material);
        button(11, icon(Material.NAME_TAG, "<gold>Nombre", "<gray>Actual: <white>" + escape(d.displayName()), "",
                "<gray>Admite MiniMessage, &códigos y placeholders", "<gray>como %uses%, %kills%..."),
                e -> ask("Nuevo nombre (MiniMessage o &códigos):", text -> {
                    def().name = text;
                    save();
                }));
        button(12, icon(Material.WRITABLE_BOOK, "<gold>Lore", loreSummary(d)), e -> new ListMenu(plugin, viewer, itemId,
                "<dark_gray>Lore · <gold>" + itemId, new ListMenu.Source() {
            @Override
            public List<String> get(CustomItem def) {
                return def.lore;
            }

            @Override
            public void set(CustomItem def, List<String> lines) {
                def.lore.clear();
                def.lore.addAll(lines);
            }
        }, null, List.of("Placeholders: %uses% %max_uses% %cooldown%", "%owner% %player% y %variable%"), this).open());
        button(13, icon(Material.ITEM_FRAME, "<gold>Custom Model Data",
                "<gray>Actual: <white>" + (d.customModelData == null ? "ninguno" : d.customModelData), "",
                "<gray>Clic: <white>escribir número</white> · Clic der: <white>quitar</white>"), e -> {
            if (e.isRightClick()) {
                def().customModelData = null;
                save();
            } else {
                ask("Custom Model Data (número entero > 0):", text -> {
                    int value = (int) parseNumber(text);
                    def().customModelData = value > 0 ? value : null;
                    save();
                });
            }
        });
        button(14, icon(Material.PAINTING, "<gold>Textura del pack <dark_gray>(opcional)",
                plugin.getConfig().getBoolean("resource-pack.enabled", false) ? "<green>Resource pack activado" : "<gray>Resource pack desactivado: se ignora",
                "<gray>Textura: <white>" + (d.texture == null ? "ninguna" : d.texture),
                "<gray>Modelo: <white>" + (d.model == null ? "auto" : d.model),
                "<gray>item-model: <white>" + (d.itemModel == null ? "auto" : d.itemModel), "",
                "<gray>Clic: <white>nombre del PNG en textures/items/</white>",
                "<gray>Shift+clic: <white>alternar handheld/generated/auto</white>",
                "<gray>Clic der: <white>quitar textura</white>"), e -> {
            if (e.isShiftClick()) {
                CustomItem def = def();
                def.model = def.model == null ? "handheld" : def.model.equals("handheld") ? "generated" : null;
                save();
            } else if (e.isRightClick()) {
                def().texture = null;
                save();
            } else {
                ask("Nombre de la textura (archivo textures/items/<nombre>.png):", text -> {
                    String clean = text.toLowerCase(Locale.ROOT).replace(".png", "").trim();
                    if (!clean.matches("[a-z0-9_./-]+")) {
                        throw new IllegalArgumentException("Solo minúsculas, números y _ - . /");
                    }
                    def().texture = clean;
                    save();
                });
            }
        });
        button(15, glow(icon(Material.GLOWSTONE_DUST, "<gold>Brillo", "<gray>Activo: " + onOff(d.glow), "<gray>Clic para alternar"), d.glow), e -> {
            def().glow = !def().glow;
            save();
        });
        button(16, icon(Material.ANVIL, "<gold>Irrompible", "<gray>Activo: " + onOff(d.unbreakable), "<gray>Clic para alternar"), e -> {
            def().unbreakable = !def().unbreakable;
            save();
        });

        // ── row 2: stats ──
        button(19, icon(Material.ENCHANTED_BOOK, "<gold>Encantamientos", enchantSummary(d)), e -> new EnchantMenu(plugin, viewer, itemId, this).open());
        button(20, icon(Material.WHITE_BANNER, "<gold>Flags", flagSummary(d)), e -> new FlagsMenu(plugin, viewer, itemId, this).open());
        button(21, icon(Material.IRON_SWORD, "<gold>Atributos", attributeSummary(d)), e -> new ListMenu(plugin, viewer, itemId,
                "<dark_gray>Atributos · <gold>" + itemId, new ListMenu.Source() {
            @Override
            public List<String> get(CustomItem def) {
                return def.attributes;
            }

            @Override
            public void set(CustomItem def, List<String> lines) {
                def.attributes.clear();
                def.attributes.addAll(lines);
            }
        }, EditorMenu::validateAttribute, List.of("Formato: ATRIBUTO: cantidad [ranura] [operación]",
                "Ej: ATTACK_DAMAGE: 8 mainhand", "MOVEMENT_SPEED: 0.1 offhand MULTIPLY", "MAX_HEALTH: 4 armor",
                "Ranuras: any mainhand offhand hand head chest legs feet armor"), this).open());
        button(22, icon(Material.EXPERIENCE_BOTTLE, "<gold>Usos máximos",
                "<gray>Actual: <white>" + (d.maxUses <= 0 ? "ilimitados" : d.maxUses), "",
                "<gray>Izq: <white>+1</white> · Der: <white>-1</white>", "<gray>Shift: <white>±10</white> · Central/Q: <white>escribir</white>"), e -> {
            int delta = switch (e.getClick()) {
                case LEFT -> 1;
                case RIGHT -> -1;
                case SHIFT_LEFT -> 10;
                case SHIFT_RIGHT -> -10;
                default -> 0;
            };
            if (delta == 0) {
                ask("Usos máximos (0 = ilimitados):", text -> {
                    def().maxUses = Math.max(0, (int) parseNumber(text));
                    save();
                });
            } else {
                def().maxUses = Math.max(0, def().maxUses + delta);
                save();
            }
        });
        button(23, icon(Material.FLINT_AND_STEEL, "<gold>Romper al agotarse",
                "<gray>Activo: " + onOff(d.breakOnEmpty), "<gray>Si no, el ítem queda inútil con 0 usos."), e -> {
            def().breakOnEmpty = !def().breakOnEmpty;
            save();
        });
        button(24, icon(Material.COMPARATOR, "<gold>Variables PDC", variableSummary(d)), e -> new ListMenu(plugin, viewer, itemId,
                "<dark_gray>Variables · <gold>" + itemId, new ListMenu.Source() {
            @Override
            public List<String> get(CustomItem def) {
                List<String> out = new ArrayList<>();
                def.variables.forEach((k, v) -> out.add(k + "=" + v));
                return out;
            }

            @Override
            public void set(CustomItem def, List<String> lines) {
                Map<String, String> vars = new LinkedHashMap<>();
                for (String line : lines) {
                    int eq = line.indexOf('=');
                    vars.put(ItemData.cleanVar(line.substring(0, eq)), line.substring(eq + 1).trim());
                }
                def.variables.clear();
                def.variables.putAll(vars);
            }
        }, line -> line.indexOf('=') > 0 ? null : "Formato: nombre=valor (ej. kills=0)",
                List.of("Formato: nombre=valor", "Se guardan en el PDC de cada copia", "y se muestran con %nombre% en el lore"), this).open());
        button(25, icon(Material.PLAYER_HEAD, "<gold>Color / Cabeza",
                "<gray>Color: <white>" + (d.color == null ? "ninguno" : d.color),
                "<gray>Textura cabeza: <white>" + (d.skullTexture == null ? "ninguna" : "base64 (" + d.skullTexture.length() + ")"), "",
                "<gray>Clic: <white>color #rrggbb (cuero/poción)</white>",
                "<gray>Shift+clic: <white>textura base64 de cabeza</white>",
                "<gray>Clic der: <white>quitar ambos</white>"), e -> {
            if (e.isRightClick() && !e.isShiftClick()) {
                def().color = null;
                def().skullTexture = null;
                save();
            } else if (e.isShiftClick()) {
                ask("Textura base64 de la cabeza (value de minecraft-heads):", text -> {
                    def().skullTexture = text.trim();
                    save();
                });
            } else {
                ask("Color en formato #rrggbb o r,g,b:", text -> {
                    def().color = text.trim();
                    save();
                });
            }
        });

        // ── row 3: restrictions ──
        toggle(28, Material.CRAFTING_TABLE, "Se puede craftear", d.canCraft, def -> def.canCraft = !def.canCraft);
        toggle(29, Material.FURNACE, "Se puede fundir/quemar", d.canSmelt, def -> def.canSmelt = !def.canSmelt);
        toggle(30, Material.DAMAGED_ANVIL, "Se puede usar en yunque", d.canAnvil, def -> def.canAnvil = !def.canAnvil);
        toggle(31, Material.DROPPER, "Se puede soltar", d.canDrop, def -> def.canDrop = !def.canDrop);
        toggle(32, Material.TOTEM_OF_UNDYING, "Se conserva al morir", d.keepOnDeath, def -> def.keepOnDeath = !def.keepOnDeath);
        toggle(33, Material.ENDER_EYE, "Soulbound (vinculado)", d.soulbound, def -> def.soulbound = !def.soulbound);
        toggle(36, Material.SHULKER_BOX, "Se puede guardar en cofres", d.canStore, def -> def.canStore = !def.canStore);
        button(34, icon(Material.AMETHYST_SHARD, "<gold>Recarga de usos",
                "<gray>Material: <white>" + (d.rechargeMaterial == null ? "ninguno" : d.rechargeMaterial),
                "<gray>Usos por unidad: <white>" + d.rechargeUses, "",
                "<gray>El jugador hace clic sobre el ítem en su", "<gray>inventario con el material en el cursor.", "",
                "<gray>Clic: <white>escribir \"MATERIAL usos\"</white> · Clic der: <white>quitar</white>"), e -> {
            if (e.isRightClick()) {
                def().rechargeMaterial = null;
                save();
            } else {
                ask("Material y usos por unidad (ej. AMETHYST_SHARD 5):", text -> {
                    String[] p = text.trim().split("\\s+");
                    Material m = Material.matchMaterial(p[0]);
                    if (m == null) {
                        throw new IllegalArgumentException("Material desconocido: " + p[0]);
                    }
                    def().rechargeMaterial = m.name();
                    def().rechargeUses = p.length > 1 ? Math.max(1, (int) parseNumber(p[1])) : 1;
                    save();
                });
            }
        });

        // ── row 4: activators ──
        List<String> actLore = new ArrayList<>();
        d.activators.forEach(a -> actLore.add("<dark_gray>• <yellow>" + a.key + "</yellow> <gray>" + a.type.name()
                + (a.cooldown > 0 ? " <dark_gray>(" + Expr.format(a.cooldown) + "s)" : "")));
        if (actLore.isEmpty()) {
            actLore.add("<gray>Sin activadores.");
        }
        actLore.add("");
        actLore.add("<gray>Clic para gestionar activadores,");
        actLore.add("<gray>cooldowns, condiciones y acciones.");
        button(40, glow(icon(Material.BLAZE_POWDER, "<gold><b>Activadores</b> <gray>(" + d.activators.size() + ")", actLore), true),
                e -> new ActivatorListMenu(plugin, viewer, itemId, this).open());

        button(38, icon(Material.IRON_BARS, "<#FB923C>Permiso del ítem",
                "<gray>Permiso: <white>" + (d.permission == null ? "libre (todos)" : d.permission),
                "<gray>Mensaje: <white>" + (d.noPermissionMessage == null ? "el de config.yml" : escape(d.noPermissionMessage)), "",
                "<gray>Clic: <white>escribir el nodo</white> (ej. radiantitems.use." + itemId + ")",
                "<gray>Shift+clic: <white>mensaje sin permiso</white>",
                "<gray>Clic der: <white>quitar permiso y mensaje</white>",
                "<dark_gray>Bypass: radiantitems.bypass.permission"), e -> {
            if (e.isShiftClick()) {
                ask("Mensaje cuando no tiene permiso (MiniMessage):", text -> {
                    def().noPermissionMessage = text;
                    save();
                });
            } else if (e.isRightClick()) {
                def().permission = null;
                def().noPermissionMessage = null;
                save();
            } else {
                ask("Permiso necesario (ej. radiantitems.use." + itemId + "):", text -> {
                    String node = text.trim();
                    if (!node.matches("[A-Za-z0-9_.*-]+")) {
                        throw new IllegalArgumentException("Permiso inválido: " + node);
                    }
                    def().permission = node;
                    save();
                });
            }
        });
        button(42, icon(Material.BOOKSHELF, "<#FB923C>Carpeta / categoría",
                "<gray>Actual: <white>items/" + (d.category.isEmpty() ? "" : d.category + "/") + (d.source == null ? itemId + ".yml" : d.source.getName()), "",
                "<gray>Clic: <white>mover a otra carpeta</white> (ej. pvp/espadas)",
                "<gray>Clic der: <white>mover a la raíz de items/</white>"), e -> {
            if (e.isRightClick()) {
                def().category = "";
                save();
            } else {
                ask("Carpeta dentro de items/ (ej. pvp o pvp/espadas):", text -> {
                    def().category = dev.privalo.items.item.ItemRegistry.cleanCategory(text);
                    save();
                });
            }
        });

        // ── row 5: misc ──
        button(45, icon(Material.BARRIER, "<red>Cerrar", "<gray>Los cambios ya están guardados."), e -> viewer.closeInventory());
        button(47, icon(Material.HOPPER, "<gold>Tamaño de pila",
                "<gray>Actual: <white>" + (d.unique() ? "1 (ítem único)" : d.maxStack == null ? "vanilla" : d.maxStack), "",
                "<gray>Clic: <white>escribir 1-99</white> · Clic der: <white>vanilla</white>",
                "<dark_gray>Los ítems con usos/variables/soulbound son únicos."), e -> {
            if (e.isRightClick()) {
                def().maxStack = null;
                save();
            } else {
                ask("Tamaño máximo de pila (1-99):", text -> {
                    def().maxStack = Math.max(1, Math.min(99, (int) parseNumber(text)));
                    save();
                });
            }
        });
        button(49, icon(Material.CHEST, "<green>Obtener ítem", "<gray>Clic: <white>1</white> · Shift+clic: <white>pila</white>"), e -> {
            int amount = e.isShiftClick() ? 64 : 1;
            plugin.giveStacks(def(), amount).forEach(s -> viewer.getInventory().addItem(s)
                    .values().forEach(o -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), o)));
        });
        button(51, icon(Material.CLOCK, "<gold>Archivo", "<gray>" + (d.source == null ? "sin guardar" : d.source.getName()),
                "<gray>Revisión: <white>" + Integer.toHexString(d.revision)), null);
        button(53, icon(Material.LAVA_BUCKET, "<red>Eliminar ítem", "<gray>Shift+clic derecho para mover",
                "<gray>el archivo a items/.trash/"), e -> {
            if (e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                try {
                    plugin.items().delete(itemId);
                    plugin.messages().send(viewer, "deleted", "id", itemId);
                } catch (IOException ex) {
                    viewer.sendMessage("Error: " + ex.getMessage());
                }
                viewer.closeInventory();
            }
        });
    }

    private static Material iconMaterial(CustomItem d) {
        Material m = Material.matchMaterial(d.material);
        return m == null || !m.isItem() ? Material.PAPER : m;
    }

    private void material(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            def().material = cursor.getType().name();
            save();
            return;
        }
        ask("Material (ej. DIAMOND_SWORD):", text -> {
            Material m = Material.matchMaterial(text.trim());
            if (m == null || !m.isItem() || m.isAir()) {
                throw new IllegalArgumentException("Material inválido: " + text);
            }
            def().material = m.name();
            save();
        });
    }

    private void toggle(int slot, Material material, String name, boolean value, java.util.function.Consumer<CustomItem> flip) {
        button(slot, glow(icon(material, "<gold>" + name, "<gray>Activo: " + onOff(value), "<gray>Clic para alternar"), value), e -> {
            flip.accept(def());
            save();
        });
    }

    private static double parseNumber(String text) {
        if (!Expr.isNumeric(text)) {
            throw new IllegalArgumentException("'" + text + "' no es un número");
        }
        return Expr.eval(text);
    }

    private static String validateAttribute(String line) {
        try {
            Attributes.Spec spec = Attributes.parse(line);
            if (Attributes.resolve(spec.attribute()) == null) {
                return "Atributo desconocido: " + spec.attribute();
            }
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    private static List<String> loreSummary(CustomItem d) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(6, d.lore.size()); i++) {
            out.add("<dark_gray>" + escape(d.lore.get(i)));
        }
        if (d.lore.size() > 6) {
            out.add("<dark_gray>... (" + (d.lore.size() - 6) + " más)");
        }
        out.add("");
        out.add("<gray>Clic para editar las líneas.");
        return out;
    }

    private static List<String> enchantSummary(CustomItem d) {
        List<String> out = new ArrayList<>();
        d.enchantments.forEach((k, v) -> out.add("<dark_gray>• <white>" + k + " " + v));
        if (out.isEmpty()) {
            out.add("<gray>Ninguno.");
        }
        out.add("");
        out.add("<gray>Clic para editar.");
        return out;
    }

    private static List<String> flagSummary(CustomItem d) {
        List<String> out = new ArrayList<>();
        d.flags.forEach(f -> out.add("<dark_gray>• <white>" + f));
        if (out.isEmpty()) {
            out.add("<gray>Ninguno.");
        }
        out.add("");
        out.add("<gray>Clic para editar.");
        return out;
    }

    private static List<String> attributeSummary(CustomItem d) {
        List<String> out = new ArrayList<>();
        d.attributes.forEach(a -> out.add("<dark_gray>• <white>" + a));
        if (out.isEmpty()) {
            out.add("<gray>Ninguno (atributos vanilla).");
        }
        out.add("");
        out.add("<gray>Clic para editar.");
        return out;
    }

    private static List<String> variableSummary(CustomItem d) {
        List<String> out = new ArrayList<>();
        d.variables.forEach((k, v) -> out.add("<dark_gray>• <white>" + k + " = " + escape(v)));
        if (out.isEmpty()) {
            out.add("<gray>Ninguna.");
        }
        out.add("");
        out.add("<gray>Clic para editar (nombre=valor).");
        return out;
    }
}
