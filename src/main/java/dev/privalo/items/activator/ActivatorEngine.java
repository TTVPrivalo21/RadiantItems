package dev.privalo.items.activator;

import dev.privalo.items.RadiantItems;
import dev.privalo.items.condition.Condition;
import dev.privalo.items.item.CustomItem;
import dev.privalo.items.item.ItemData;
import dev.privalo.items.item.ItemRef;
import dev.privalo.items.util.Messages;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Runs the activators of an item for a trigger: ownership → uses → cooldown → conditions →
 * chance → costs → consumption → actions → success message.
 */
public final class ActivatorEngine {

    private final RadiantItems plugin;

    public ActivatorEngine(RadiantItems plugin) {
        this.plugin = plugin;
    }

    /**
     * Fires every activator of {@code type} on the item in {@code ref}.
     *
     * @return true when the original event should be cancelled
     */
    public boolean fire(ActivatorType type, Player player, ItemRef ref, Consumer<ActivationContext> init, Predicate<Activator> filter) {
        if (ref == null) {
            return false;
        }
        ItemStack stack = ref.get();
        String id = ItemData.id(stack);
        CustomItem def = plugin.items().get(id);
        if (def == null || !def.has(type)) {
            return false;
        }
        ActivationContext base = new ActivationContext(plugin, player, def, ref, type);
        if (init != null) {
            init.accept(base);
        }
        boolean cancel = false;
        List<Activator> matching = new ArrayList<>();
        for (Activator a : def.activators) {
            if (a.type != type || (filter != null && !filter.test(a)) || !matches(a, base)) {
                continue;
            }
            if (a.cancels()) {
                cancel = true;
            }
            matching.add(a);
        }
        if (matching.isEmpty()) {
            return false;
        }
        if (!hasItemPermission(player, def)) {
            if (!type.slotted()) {
                String template = def.noPermissionMessage != null ? def.noPermissionMessage : plugin.messages().rawOrNull("activator.no-item-permission");
                if (template != null && !template.isEmpty()) {
                    plugin.messages().sendRaw(player, base.format(Messages.apply(template, "permission", def.permission)));
                }
            }
            return cancel;
        }
        for (Activator a : matching) {
            run(base.fork(a));
        }
        return cancel;
    }

    /** Item-level permission (empty = everyone), with a configurable admin bypass. */
    public boolean hasItemPermission(Player player, CustomItem def) {
        if (def.permission == null || def.permission.isBlank()) {
            return true;
        }
        String bypass = plugin.getConfig().getString("permissions.bypass-item-permission", "radiantitems.bypass.permission");
        return player.hasPermission(def.permission) || (bypass != null && !bypass.isBlank() && player.hasPermission(bypass));
    }

    private static boolean matches(Activator a, ActivationContext ctx) {
        if (a.type.click()) {
            if (a.click == Activator.ClickMode.AIR && ctx.block() != null) {
                return false;
            }
            if (a.click == Activator.ClickMode.BLOCK && ctx.block() == null) {
                return false;
            }
        }
        Entity target = ctx.target();
        if (a.entities != Activator.EntityFilter.ANY && target != null) {
            if (a.entities == Activator.EntityFilter.PLAYER && !(target instanceof Player)) {
                return false;
            }
            if (a.entities == Activator.EntityFilter.MOB && (target instanceof Player || !(target instanceof Mob))) {
                return false;
            }
        }
        return true;
    }

    private void run(ActivationContext ctx) {
        Activator a = ctx.activator();
        CustomItem def = ctx.item();
        Player player = ctx.player();
        ItemStack stack = ctx.ref().get();
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean quiet = a.type.slotted();

        UUID owner = ItemData.owner(pdc);
        if (def.soulbound && owner != null && !owner.equals(player.getUniqueId()) && !player.hasPermission("radiantitems.bypass.soulbound")) {
            if (!quiet) {
                message(ctx, "not-owner", "owner", ItemData.ownerName(pdc));
            }
            return;
        }
        int uses = def.maxUses > 0 ? ItemData.uses(pdc, def.maxUses) : -1;
        if (a.consumeUsage > 0 && def.maxUses > 0 && uses < a.consumeUsage) {
            if (!quiet) {
                message(ctx, "no-uses");
            }
            return;
        }
        String cooldownKey = Cooldowns.key(a, def, player, ItemData.uuid(pdc));
        double left = plugin.cooldowns().remaining(cooldownKey);
        if (left > 0) {
            if (a.cooldownMessage && !quiet) {
                message(ctx, "cooldown", "time", String.format(Locale.ROOT, "%.1f", left));
            }
            return;
        }
        for (Condition condition : a.parsedConditions()) {
            if (!condition.test(ctx)) {
                if (!quiet || condition.message() != null) {
                    conditionMessage(ctx, condition);
                }
                return;
            }
        }
        if (a.chance < 100 && ThreadLocalRandom.current().nextDouble(100) >= a.chance) {
            String chanceMsg = a.messages.get("chance-failed");
            if (chanceMsg != null) {
                plugin.messages().sendRaw(player, ctx.format(chanceMsg));
            }
            return;
        }
        for (Condition condition : a.parsedConditions()) {
            condition.charge(ctx);
        }
        plugin.cooldowns().set(cooldownKey, a.cooldown);

        boolean changed = false;
        if (def.soulbound && owner == null) {
            ItemData.owner(pdc, player.getUniqueId(), player.getName());
            plugin.messages().send(player, "soulbound-bound");
            changed = true;
        }
        boolean broke = false;
        if (a.consumeUsage > 0 && def.maxUses > 0) {
            uses -= a.consumeUsage;
            ItemData.setUses(pdc, uses);
            changed = true;
            if (uses <= 0 && def.breakOnEmpty) {
                broke = true;
            }
        }
        if (changed) {
            plugin.factory().render(def, meta, player);
            stack.setItemMeta(meta);
        }
        if (broke) {
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() > 0) {
                ItemMeta rest = stack.getItemMeta();
                ItemData.setUses(rest.getPersistentDataContainer(), def.maxUses);
                plugin.factory().render(def, rest, player);
                stack.setItemMeta(rest);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            message(ctx, "item-broke");
        } else if (a.consumeItem) {
            stack.setAmount(stack.getAmount() - 1);
        }
        if (changed || broke || a.consumeItem) {
            ctx.ref().set(stack);
        }
        plugin.actions().run(a.parsedActions(), ctx);
        String success = a.messages.get("success");
        if (success != null && !success.isEmpty()) {
            plugin.messages().sendRaw(player, ctx.format(success));
        }
    }

    /** Activator message override → messages.activator.&lt;key&gt;. */
    public void message(ActivationContext ctx, String key, String... kv) {
        Activator a = ctx.activator();
        String template = a == null ? null : a.messages.get(key);
        if (template == null) {
            template = plugin.messages().rawOrNull("activator." + key);
        }
        if (template == null || template.isEmpty()) {
            return;
        }
        String text = Messages.apply(template, kv);
        if (a != null && "actionbar".equalsIgnoreCase(a.messages.get("mode"))) {
            plugin.messages().actionBar(ctx.player(), ctx.format(text));
        } else {
            plugin.messages().sendRaw(ctx.player(), ctx.format(text));
        }
    }

    private void conditionMessage(ActivationContext ctx, Condition condition) {
        String template = condition.message();
        if (template == null) {
            template = plugin.messages().rawOrNull(condition.negated() ? "conditions.NEGATED" : "conditions." + condition.type().name());
        }
        if (template == null || template.isEmpty()) {
            return;
        }
        plugin.messages().sendRaw(ctx.player(), ctx.format(Messages.apply(template, "value", ctx.format(condition.value()))));
    }
}
