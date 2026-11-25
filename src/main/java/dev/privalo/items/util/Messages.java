package dev.privalo.items.util;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Messages from config.yml → messages.* with %placeholder% replacement. */
public final class Messages {

    private ConfigurationSection section;
    private String prefix = "";

    public void load(FileConfiguration config) {
        section = config.getConfigurationSection("messages");
        prefix = section == null ? "" : section.getString("prefix", "");
    }

    /** Raw template, or the key itself if missing. */
    public String raw(String key) {
        if (section == null) {
            return key;
        }
        return section.getString(key, key);
    }

    public String rawOrNull(String key) {
        return section == null ? null : section.getString(key);
    }

    public static String apply(String template, String... kv) {
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("%" + kv[i] + "%", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return out;
    }

    public void send(CommandSender sender, String key, String... kv) {
        sendRaw(sender, raw(key), kv);
    }

    /** Sends a template; empty templates are skipped, multi-line ones are split. */
    public void sendRaw(CommandSender sender, String template, String... kv) {
        if (template == null || template.isEmpty()) {
            return;
        }
        String text = apply(template, kv);
        for (String line : text.split("\n")) {
            sender.sendMessage(Text.parse(prefix + line));
        }
    }

    public void actionBar(Player player, String template, String... kv) {
        if (template == null || template.isEmpty()) {
            return;
        }
        player.sendActionBar(Text.parse(apply(template, kv)));
    }
}
