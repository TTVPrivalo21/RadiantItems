package dev.privalo.items.pack;

import java.util.Locale;

/** Procedural 16x16 icons for the bundled example items (used when no PNG exists in textures/items/). */
public final class IconPainter {

    private static final int OUTLINE = 0xFF1B1420;

    private IconPainter() {
        throw new AssertionError("Clase de utilidades: no se instancia");
    }

    /** Returns the procedural icon for a texture name, or null when there is none. */
    public static PixelCanvas paint(String texture) {
        String key = texture.toLowerCase(Locale.ROOT);
        int slash = key.lastIndexOf('/');
        if (slash >= 0) {
            key = key.substring(slash + 1);
        }
        PixelCanvas c = new PixelCanvas(16, 16);
        switch (key) {
            case "grappling_hook" -> hook(c);
            case "lightning_staff" -> staff(c);
            case "vampiric_sword" -> sword(c);
            case "explosive_pickaxe" -> pickaxe(c);
            case "speed_amulet" -> amulet(c);
            case "teleport_scroll" -> scroll(c);
            case "phoenix_elixir" -> elixir(c);
            default -> {
                return null;
            }
        }
        outline(c);
        return c;
    }

    /** 64x64 pack.png: gradient badge with the sword icon scaled up. */
    public static PixelCanvas packIcon() {
        PixelCanvas c = new PixelCanvas(64, 64);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                double d = Math.hypot(x - 31.5, y - 31.5) / 45.0;
                c.set(x, y, PixelCanvas.mix(0xFF3A1D5C, 0xFF0E0A16, Math.min(1, d)));
            }
        }
        c.roundStroke(0, 0, 64, 64, 0xFFFFB347);
        c.roundStroke(1, 1, 62, 62, 0xFFFF7A3D);
        PixelCanvas sword = paint("vampiric_sword");
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = sword.get(x, y);
                if ((color >>> 24) != 0) {
                    c.blendFill(8 + x * 3, 8 + y * 3, 3, 3, color);
                }
            }
        }
        return c;
    }

    // ───────────────────────── primitives ─────────────────────────

    private static void line(PixelCanvas c, double x0, double y0, double x1, double y1, int color) {
        double steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2 + 1;
        for (int i = 0; i <= steps; i++) {
            double t = i / steps;
            c.set((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), color);
        }
    }

    private static void disc(PixelCanvas c, double cx, double cy, double r, int inner, int outer) {
        for (int y = 0; y < c.height(); y++) {
            for (int x = 0; x < c.width(); x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d <= r) {
                    c.set(x, y, PixelCanvas.mix(inner, outer, Math.min(1, d / Math.max(0.01, r))));
                }
            }
        }
    }

    private static void arc(PixelCanvas c, double cx, double cy, double r, double from, double to, int color) {
        for (double a = from; a <= to; a += 0.05) {
            c.set((int) Math.round(cx + Math.cos(a) * r), (int) Math.round(cy + Math.sin(a) * r), color);
        }
    }

    /** Dark 1px outline around every opaque pixel (classic item-sprite look). */
    private static void outline(PixelCanvas c) {
        boolean[][] solid = new boolean[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                solid[y][x] = (c.get(x, y) >>> 24) > 0;
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (solid[y][x]) {
                    continue;
                }
                boolean edge = (x > 0 && solid[y][x - 1]) || (x < 15 && solid[y][x + 1])
                        || (y > 0 && solid[y - 1][x]) || (y < 15 && solid[y + 1][x]);
                if (edge) {
                    c.set(x, y, OUTLINE);
                }
            }
        }
    }

    // ───────────────────────── icons ─────────────────────────

    private static void hook(PixelCanvas c) {
        line(c, 9, 1, 14, 0, 0xFF8B5A2B);
        line(c, 10, 2, 15, 1, 0xFF6E4421);
        line(c, 8, 2, 8, 10, 0xFFB8C4CC);
        line(c, 9, 3, 9, 9, 0xFF7F8C96);
        arc(c, 5.5, 9.5, 3, 0, Math.PI, 0xFFDCE6EC);
        arc(c, 5.5, 9.5, 2, 0.2, Math.PI - 0.2, 0xFF8C9AA4);
        line(c, 2, 9, 2, 6, 0xFFDCE6EC);
        c.set(3, 5, 0xFFFFFFFF);
        c.set(8, 1, 0xFF5A6670);
    }

    private static void staff(PixelCanvas c) {
        line(c, 2, 14, 10, 6, 0xFF7A4A24);
        line(c, 3, 14, 11, 6, 0xFF54321A);
        line(c, 9, 7, 11, 5, 0xFFD4A537);
        disc(c, 12, 3.5, 2.6, 0xFFFFFFFF, 0xFF3FC8FF);
        c.set(15, 1, 0xFFFFF27A);
        c.set(14, 0, 0xFFFFF27A);
        c.set(9, 1, 0xFFFFF27A);
        c.set(10, 0, 0xFFFFF27A);
        c.set(15, 6, 0xFFFFF27A);
    }

    private static void sword(PixelCanvas c) {
        line(c, 5, 10, 14, 1, 0xFFE8E2E6);
        line(c, 6, 10, 14, 2, 0xFFB01E35);
        line(c, 5, 9, 13, 1, 0xFFC9BFC6);
        line(c, 2, 8, 7, 13, 0xFFE0B341);
        line(c, 3, 8, 7, 12, 0xFFA87A22);
        line(c, 4, 11, 1, 14, 0xFF5E1520);
        c.set(1, 15, 0xFFE0B341);
        c.set(0, 14, 0xFFE0B341);
    }

    private static void pickaxe(PixelCanvas c) {
        line(c, 2, 14, 10, 6, 0xFF8B5A2B);
        line(c, 3, 14, 11, 6, 0xFF5E3B1B);
        for (double t = 0; t <= 1.0; t += 0.02) {
            double x = (1 - t) * (1 - t) * 4 + 2 * (1 - t) * t * 14 + t * t * 13;
            double y = (1 - t) * (1 - t) * 2 + 2 * (1 - t) * t * 1 + t * t * 12;
            int color = PixelCanvas.mix(0xFFFF5A2B, 0xFFFFC23D, Math.abs(0.5 - t) * 2);
            c.set((int) Math.round(x), (int) Math.round(y), color);
            c.set((int) Math.round(x) - 1, (int) Math.round(y) + 1, PixelCanvas.mix(color, 0xFF7A1E0E, 0.45));
        }
        c.set(4, 2, 0xFF2D2D2D);
        c.set(13, 12, 0xFF2D2D2D);
        c.set(15, 0, 0xFFFFF27A);
        c.set(14, 0, 0xFFFFA63D);
    }

    private static void amulet(PixelCanvas c) {
        arc(c, 7.5, 6, 5.5, Math.PI, Math.PI * 2, 0xFFE8C04A);
        line(c, 2, 6, 5, 10, 0xFFE8C04A);
        line(c, 13, 6, 10, 10, 0xFFE8C04A);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.abs(x - 7.5) + Math.abs(y - 11.5);
                if (d <= 3.6) {
                    c.set(x, y, PixelCanvas.mix(0xFF9BFFD0, 0xFF1C9E6B, Math.min(1, d / 3.6)));
                }
            }
        }
        c.set(6, 10, 0xFFFFFFFF);
        c.set(7, 9, 0xFFE6FFF4);
    }

    private static void scroll(PixelCanvas c) {
        c.fill(4, 3, 8, 10, 0xFFEBD9A6);
        c.fill(4, 3, 1, 10, 0xFFD2BC82);
        c.fill(3, 1, 10, 2, 0xFFB8925A);
        c.fill(3, 13, 10, 2, 0xFFB8925A);
        c.hline(3, 1, 10, 0xFFD6B278);
        c.hline(3, 13, 10, 0xFFD6B278);
        arc(c, 7.5, 8, 2.6, 0, Math.PI * 2, 0xFF8E3CD6);
        c.set(7, 8, 0xFFC08AFF);
        c.set(8, 8, 0xFFC08AFF);
        c.set(7, 5, 0xFF5A2A8A);
        c.set(8, 11, 0xFF5A2A8A);
    }

    private static void elixir(PixelCanvas c) {
        disc(c, 7.5, 10.5, 4.6, 0xFFFFE08A, 0xFFE2401C);
        c.fill(6, 3, 4, 3, 0xFFD8EEF2);
        c.fill(6, 1, 4, 2, 0xFF8B5A2B);
        c.set(5, 8, 0xFFFFFFFF);
        c.set(5, 9, 0xFFFFF2D0);
        c.set(9, 6, 0xFFFFB23D);
    }
}
