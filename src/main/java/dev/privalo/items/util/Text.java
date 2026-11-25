package dev.privalo.items.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;

import java.util.Map;

/** MiniMessage + legacy (&amp;a, &amp;#rrggbb) text helpers. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));

    private Text() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    /** Converts legacy colour codes into MiniMessage tags so both syntaxes can be mixed. */
    public static String legacyToMini(String raw) {
        if (raw.indexOf('&') < 0 && raw.indexOf('§') < 0) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < raw.length()) {
                char n = Character.toLowerCase(raw.charAt(i + 1));
                if (n == '#' && i + 7 < raw.length() && isHex(raw, i + 2, 6)) {
                    sb.append("<#").append(raw, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = LEGACY.get(n);
                if (tag != null) {
                    sb.append(tag);
                    i++;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isHex(String s, int from, int len) {
        for (int i = from; i < from + len; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        try {
            return MM.deserialize(legacyToMini(raw));
        } catch (RuntimeException ex) {
            return Component.text(raw);
        }
    }

    /** Parsed without the default italic that item names/lore get. */
    public static Component item(String raw) {
        return parse(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String plain(String raw) {
        return plain(parse(raw));
    }

    /** "#rrggbb", "rrggbb" or "r,g,b" → Bukkit colour. */
    public static Color color(String raw, Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String clean = raw.trim();
        try {
            if (clean.contains(",")) {
                String[] p = clean.split(",");
                return Color.fromRGB(clamp(p[0]), clamp(p[1]), clamp(p[2]));
            }
            if (clean.startsWith("#")) {
                clean = clean.substring(1);
            }
            if (clean.length() == 6) {
                return Color.fromRGB(Integer.parseInt(clean, 16));
            }
        } catch (RuntimeException ignored) {
            return fallback;
        }
        return fallback;
    }

    private static int clamp(String s) {
        return Math.max(0, Math.min(255, Integer.parseInt(s.trim())));
    }

    public static String hex(Color color) {
        return String.format("#%06x", color.asRGB());
    }

    /** "FIRE_ASPECT" → "Fire Aspect". */
    public static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(java.util.Locale.ROOT).replace(':', '_').split("[_.]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
